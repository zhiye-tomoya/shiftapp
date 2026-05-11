コードベース（Shift / ShiftRequest / Auth / ADMIN・STAFF ロール、`clockInTime`/`clockOutTime` ベースのシフトモデル）と `TODO.md` を一通り見たうえで、「これがあると一気に実用に近づく」という機能を、優先度と実装難易度の目安付きで挙げます。

---

## 🥇 優先度: 高（運用に入る前にほぼ必須）

### 1. シフト一括作成 (Bulk Create) ✅ 完了

実装内容:

- `POST /api/shifts/bulk` … 認証済みユーザー（STAFF / ADMIN）が **自分自身** に対して期間 × 曜日でまとめて DRAFT シフトを生成。所有者は JWT principal (`AuthenticatedUser.userId`) から取り、リクエストボディには含めない（`/api/shifts/me` 系と同じ IDOR 防御方針）。
- `PATCH /api/shifts/bulk/submit` … 自分が所有する DRAFT シフトを ID 配列で一括 submit（DRAFT → SUBMITTED）。所有者・ステータス・存在を ID 単位でチェックし、弾いたものは型付き理由とともに `skipped` で返す（part of TODO §2 とは別レイヤーの一括 lifecycle 操作）。
- リクエスト例（`POST /api/shifts/bulk`）:
  ```jsonc
  {
    "startDate": "2026-05-04",
    "endDate": "2026-05-08",
    "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"], // 省略すると毎日
    "clockInLocalTime": "09:00",
    "clockOutLocalTime": "18:00",
    "skipOverlapping": true, // false にすると重複時は即 409
    "atomic": false, // true なら 1 件でも skip 候補があれば全ロールバック
  }
  ```
  レスポンス: `{ created: ShiftResponse[], skipped: { date, reason: "OVERLAPPING_EXISTING_SHIFT" | ... }[] }`（`201 Created`）
- リクエスト例（`PATCH /api/shifts/bulk/submit`）:
  ```jsonc
  { "shiftIds": [101, 102, 103], "atomic": false }
  ```
  レスポンス: `{ submitted: ShiftResponse[], skipped: { shiftId, reason: "NOT_FOUND" | "NOT_OWNED_BY_REQUESTER" | "INVALID_STATUS_TRANSITION" }[] }`（`200 OK`）
- 設計上のポイント（実装で押さえたところ）:
  - `Shift.isOverlapping()` をドメインから再利用し、`ShiftRepository.findAllByUserIdAndClockInTimeBetween` で **期間 × ユーザー** の既存シフトを 1 クエリ取得 → in-memory 判定で N+1 を回避。
  - `@Transactional` + `atomic` フラグで「全部ロールバック」or「部分成功＋skipped 一覧」を切替可能。`atomic=true` で skip 候補が出た場合は `IllegalStateException` を投げて `GlobalExceptionHandler` が 409 にマップ。
  - `created` / `skipped`、`submitted` / `skipped` を分けて返すので、フロントは「どこを赤くハイライトしてリトライさせるか」を型情報だけで決められる。
- テスト: `ShiftServiceTest`（mockk で隔離した bulkCreate / bulkSubmit のロジック）+ `ShiftControllerIntegrationTest`（成功 / overlap skip / atomic ロールバック / 他人の shift 拒否 / 未認証 403 などの end-to-end）を追加。
- スコープ外（将来タスクのまま残す）:
  - `POST /api/shifts/bulk/explicit` … 日時を配列で渡す版（CSV インポートで使用予定）。
  - `POST /api/shifts/bulk/from-template` … §3「シフトテンプレート」と組み合わせる版。
  - **ADMIN が他人の shift を一括で積む** ユースケース（元の TODO の `userId` をボディで受ける案）。必要になれば `POST /api/admin/shifts/bulk` として別途生やす想定（`AdminController` に隔離して権限判定を絶対化する）。
  - `excludeHolidays` 等の祝日カレンダー連携。

---

### 2. シフト編集 / 削除 (`PUT` / `PATCH` / `DELETE /api/shifts/{id}`) ✅ 完了

実装内容:

- `PUT /api/shifts/{id}` … 編集可能フィールド (`clockInTime`/`clockOutTime`/`userId`) を全指定して置換 (`ReplaceShiftRequest`)。
- `PATCH /api/shifts/{id}` … 部分更新 (`UpdateShiftRequest`)。最低 1 フィールド必須。
- `DELETE /api/shifts/{id}` … ハード削除（必要になれば soft-delete に切替）。
- 権限マトリクス（`ShiftService.updateShift` / `deleteShift` で実施）:
  - STAFF: 自分の DRAFT のみ編集・削除可。`userId` の付け替えは禁止（IDOR 防御）。
  - ADMIN: 任意の状態を強制編集・削除可。`userId` の付け替えも可。
  - 編集してもステータスは保たれる（lifecycle は `/submit` `/approve` `/reject` 専用）。
- 楽観ロック: `Shift.@Version` + リクエスト body の `version` で事前チェック。
  ズレていれば 409 を返してから書き込み無し。送らなければ flush 時に Hibernate がバックストップ。
- 例外マッピング: `AccessDeniedException → 403`, `OptimisticLockingFailureException → 409` を `GlobalExceptionHandler` に追加。
- 統合テスト: PUT/PATCH/DELETE × ロール × ステータス × バージョン衝突 を `ShiftControllerIntegrationTest` に追加（全 132 件 green）。

### 3. シフトテンプレート (Shift Template) ✅ 完了

実装内容:

- 新エンティティ `ShiftTemplate { id, name, clockInLocalTime, clockOutLocalTime, daysOfWeek, roleTag, ownerId, version }`
  - `daysOfWeek` は `@ElementCollection`（`shift_template_days` テーブル）。
  - `ownerId == null` = **共有テンプレ**（ADMIN のみ作成・編集）、`ownerId == <userId>` = **個人テンプレ**（本人＋ADMIN のみ編集）。
  - ドメイン invariants: `clockOut > clockIn` / `daysOfWeek` 非空 / `name` 非空白。`@Version` で楽観ロック。
- CRUD エンドポイント（`/api/shift-templates`、全認証必須）:
  - `GET /api/shift-templates` … 自分のテンプレ＋共有テンプレを `name ASC` で返す。
  - `GET /api/shift-templates/{id}` … 個人テンプレは所有者＋ADMIN のみ閲覧可。
  - `POST /api/shift-templates` … STAFF は個人テンプレのみ。`shared=true` は STAFF だと **silent downgrade**（フロントが UI を出し忘れた時の安全側）。ADMIN のみ `shared=true` で共有テンプレ作成可。
  - `PATCH /api/shift-templates/{id}` … 部分更新＋楽観ロック（`version`）。STAFF は **自分の個人テンプレのみ**、ADMIN は任意。
  - `DELETE /api/shift-templates/{id}` … 同上の権限マトリクス。既存シフトは template ID を保持していないので影響なし。
- 適用エンドポイント:
  - `POST /api/shifts/bulk/from-template { templateId, startDate, endDate, skipOverlapping?, atomic? }`
  - 所有者は **JWT principal 固定**（STAFF が他人に展開できない、bulk-create と同じ IDOR 防御方針）。
  - 中身は `ShiftTemplateService.apply` → `ShiftService.bulkCreate` に委譲して、重複検出 / atomic ロールバック / partial-success レスポンスを **§1 と完全に共有**（同じ `BulkCreateShiftResponse`）。
- リクエスト例（`POST /api/shift-templates`）:
  ```jsonc
  {
    "name": "平日 9-18",
    "clockInLocalTime": "09:00",
    "clockOutLocalTime": "18:00",
    "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "roleTag": "ホール", // 任意の自由タグ。§15 で `position` 専用 FK に昇格予定。
    "shared": false, // ADMIN のみ true 可（STAFF は silently false に矯正）
  }
  ```
- リクエスト例（`POST /api/shifts/bulk/from-template`）:
  ```jsonc
  {
    "templateId": 7,
    "startDate": "2026-05-04",
    "endDate": "2026-05-08",
    "skipOverlapping": true,
    "atomic": false,
  }
  ```
  レスポンス: `BulkCreateShiftResponse`（§1 と同形 — `created[]` / `skipped[]`、201 Created）。
- 権限マトリクス（実装で押さえた表）:

  | Op            | STAFF                    | ADMIN        |
  | ------------- | ------------------------ | ------------ |
  | list          | 自分 + 共有              | 自分 + 共有  |
  | get(id)       | 自分 + 共有              | 任意         |
  | create        | 個人のみ（ownerId=自分） | 個人 or 共有 |
  | update/delete | 自分の個人テンプレのみ   | 任意         |
  | apply         | 閲覧可能な任意テンプレ   | 同左         |

- テスト: `ShiftTemplateTest`（ドメイン invariants）+ `ShiftTemplateControllerIntegrationTest`（CRUD × ロール × 共有/個人、PATCH の merged-time バリデーション、楽観ロック衝突、apply 経由の bulk 結合、未認証 403 …計 18 ケース）を追加。全 155 件 green。
- スコープ外（将来タスク）:
  - **個人 → 共有への昇格（あるいは逆）** の専用エンドポイント。PATCH に混ぜると権限昇格パスが増えるので分離する想定。
  - **ADMIN が他人のために apply** するユースケース（`POST /api/admin/shifts/bulk/from-template` として §1 のADMIN 版と一緒に追加予定）。
  - 「テンプレから生成されたシフトであることを保持する `sourceTemplateId`」… 監査ログ (§13) と一緒に検討。
  - `roleTag` の自由文字列 → §15「`position` / `role` / `skill` タグ」で正規化される時に FK 化。

### 4. シフト確定（公開）フロー

今は DRAFT → SUBMITTED → APPROVED だが、「**月のシフト表として確定して全員に公開**」というステップが無い。

- 新ステータス `PUBLISHED`、または `ShiftSchedule`（月単位の集約）エンティティを導入
- `POST /api/schedules/{yyyy-MM}/publish` で「その月の APPROVED シフトをまとめて公開」
- 公開後の編集は版管理 or イベントログ

### 5. 自分のシフトを一覧 (`GET /api/shifts/me`)

今は `/api/shifts/user/{userId}` で他人の userId も叩けてしまう（TODO の「所有者チェック」とつながる）。
JWT から userId を取って `me` エンドポイントを生やすのが一番安全 & フロントが楽。

---

## 🥈 優先度: 中（プロダクトの "らしさ" が出る機能）

### 6. シフト希望提出 (Shift Preference / Availability)

STAFF が「来週のこの時間帯に入れます／入れません」を出す機能。これがあると ADMIN の bulk 作成が「希望ベースの自動割当」につながる。

- `Availability { userId, date, fromTime, toTime, type: PREFERRED|UNAVAILABLE }`
- `POST /api/availabilities`、`GET /api/availabilities?userId&from&to`
- ADMIN 画面で「希望と矛盾する割当」を赤くハイライト

### 7. シフト自動生成 (Auto Scheduling)

6 番の希望と「曜日ごとに最低 N 人必要」みたいな要件 (`StaffingRequirement`) からシフト案を自動生成。

- 最初は素朴な貪欲法 / 整数計画でも十分価値あり
- `POST /api/schedules/{yyyy-MM}/generate` → DRAFT のシフト群を返す → ADMIN が微調整して publish

### 8. 通知 / イベント (Notifications)

- ドメインイベント: `ShiftSubmitted`, `ShiftApproved`, `SwapRequested`, `SwapApprovedByTarget` など
- 配信先: メール、Web Push、フロントの未読バッジ用 `GET /api/notifications`
- まずは DB に `notifications` テーブル作って poll、その後 SSE / WebSocket に拡張

### 9. 出退勤打刻 (Clock-in/Clock-out)

今の `clockInTime` は **予定時刻** だけど、実態には **実打刻時刻** が必要。

- `Shift` に `actualClockInTime`, `actualClockOutTime` を追加 or `Attendance` エンティティを別建て
- `POST /api/shifts/{id}/clock-in`, `POST /api/shifts/{id}/clock-out`
- 後述の「労務レポート」「給与計算」につながる主要データになる

### 10. 検索の強化 — 自分のシフトもページング & 期間絞り込み

今 `getShiftsByUser` はリスト全返し。月カレンダー UI を作るとすぐ重くなる。

- `GET /api/shifts/me?from&to&status&page&size&sort`（ADMIN 一覧と対称な形に）
- `GET /api/shifts?...` の期間フィルタ (`from`/`to`) は ADMIN 用にもう実装済みなので、それを `me` 用にも使えるように共通化

### 11. CSV インポート / エクスポート

- Import: 給与システムや既存スプレッドシートからの一括投入
  - `POST /api/shifts/import` (multipart/form-data)、行ごとに validation エラーを `{row, message}` で返す
- Export:
  - `GET /api/shifts/export.csv?from&to&userId`（ADMIN）
  - `GET /api/shifts/me/export.csv?from&to`（本人）

### 12. レポート / サマリー

- `GET /api/reports/work-hours?userId&from&to` → 期間内の総勤務時間、夜勤時間、シフト数
- `GET /api/reports/staffing?from&to` → 日付 × 必要人数 vs 実際の差分
- 給与計算の足がかりになる

---

## 🥉 優先度: 低だがあると "伸びる"

### 13. 監査ログ (Audit Log)

- `who / when / action / shiftId / before / after`
- ADMIN が「誰がいつ approve した」を追えるように。`@EntityListeners` か `Hibernate Envers`、または独自イベントログテーブル。

### 14. マルチストア / マルチ組織

`User.storeId` は既にあるので、シフトにも `storeId` を持たせて全 API でフィルタ。
将来チェーン店展開するなら早めに導入したほうが後で楽。

### 15. シフトに `position` / `role` / `skill` タグ

「レジ」「ホール」「キッチン」など職種を持たせると、自動割当やシフト表の見た目が一気に実用的になる。

### 16. 役割ベースのもう一段階細かい権限

- `MANAGER` ロール（店長: 自店舗のみ approve 可）
- リソースベースの権限: 「自分の所属店舗のシフトだけ編集可能」
- Spring Security の `@PreAuthorize("@shiftAccess.canEdit(#id, principal)")` パターン

### 17. レート制限 / Idempotency

- ログインや bulk create に Idempotency-Key 対応（同じキーが来たら同じ結果）
- 一括作成は重い処理になるので保険として欲しい

### 18. WebSocket / SSE でリアルタイム反映

- ADMIN が approve した瞬間に STAFF のカレンダーが切り替わる
- まずは `GET /api/events/stream` (SSE) が実装が軽くて十分

### 19. i18n / タイムゾーン

- `LocalDateTime` で持っているので、ストア (or User) の `ZoneId` を持って境界で変換するルールを早めに固めたほうが安全
- DST のないアジア圏中心なら後回し可

### 20. PWA / モバイル: オフラインで打刻、オンライン復帰で同期

- 9 番の打刻機能が育ってからで OK だが、現場アプリとしては差別化ポイント

---

## 推奨ロードマップ（小さく積む順）

1. **Shift `PUT` / `DELETE`**（TODO 解消、bulk の前提）
2. **`GET /api/shifts/me` ＋所有者チェック**（セキュリティ宿題と同時に解消）
3. **Bulk Create**（一番要望が出やすい運用機能）
4. **Shift Template**（bulk の親戚、UI 入力が楽になる）
5. **公開フロー (PUBLISHED)** または **打刻 (Attendance)** のどちらかを先に  
   ＝ プロダクトの方向性で分岐。「シフト管理寄り」なら 4→公開、「労務寄り」なら 4→打刻。
6. **CSV import/export と work-hours レポート**
7. **希望提出 → 自動生成**

---

ご意見聞きたいポイントは 3 つです:

- **(A)** この中でまず **どれを実装したい** ですか？（特に bulk create はどのバリアント — 「日付＋曜日パターン」型 / 「明示配列」型 / 「テンプレ展開」型 — がイメージに近いですか？）
- **(B)** プロダクトの方向は **シフト管理（公開・希望・自動割当）寄り** と **労務管理（打刻・勤怠・給与）寄り** のどちらに重心を置きたいですか？
- **(C)** 上の 1〜5（PUT/DELETE → me → bulk → template → 公開 or 打刻）を順に着手するロードマップでよさそうなら、Act mode に切り替えてもらえれば、まず **`PUT` / `DELETE /api/shifts/{id}`** から実装に入れます。
