コードベース（Shift / ShiftRequest / Auth / ADMIN・STAFF ロール、`clockInTime`/`clockOutTime` ベースのシフトモデル）と `TODO.md` を一通り見たうえで、「これがあると一気に実用に近づく」という機能を、優先度と実装難易度の目安付きで挙げます。

---

## 🥇 優先度: 高（運用に入る前にほぼ必須）

### 1. シフト一括作成 (Bulk Create) — ご提案の機能

ADMIN が「来週の月〜金、Aさんを 9:00–18:00 で」みたいに一気に積めるエンドポイント。

- **API 案**
  - `POST /api/shifts/bulk`（ADMIN only）
  - リクエスト例:
    ```jsonc
    {
      "userId": 12,
      "startDate": "2026-05-04",
      "endDate": "2026-05-08",
      "daysOfWeek": ["MON", "TUE", "WED", "THU", "FRI"],
      "clockInLocalTime": "09:00",
      "clockOutLocalTime": "18:00",
      "status": "APPROVED", // 一括投入時は最初から承認、も選べる
      "skipOverlapping": true, // 既存と重複する日はスキップ
    }
    ```
  - レスポンス: `{ created: ShiftResponse[], skipped: { date, reason }[] }`
- **派生バージョン**
  - `POST /api/shifts/bulk/explicit`: 日時を配列で渡す版（`shifts: [{userId, clockIn, clockOut}, ...]`）。CSV インポートやテンプレート展開で使う。
  - `POST /api/shifts/bulk/from-template`: 後述「シフトテンプレート」と組み合わせる。
- **設計上のポイント**
  - 1 トランザクションでまとめて `saveAll`、エラーは「全部ロールバック」or「部分成功＋エラー一覧」を選べるオプション (`atomic: true|false`)
  - 重複チェックは `Shift.isOverlapping` を再利用、ユーザー × 期間で既存シフトを 1 クエリで取得して in-memory 判定（N+1 を避ける）
  - `daysOfWeek` を絞り込みつつ祝日除外オプション (`excludeHolidays`) も将来追加余地

---

### 2. シフト編集 / 削除 (`PUT` / `DELETE /api/shifts/{id}`)

TODO.md にも残っている宿題。bulk より先に普通の単体編集が無いと、bulk で作って間違えたシフトを直せません。

- 編集可能フィールド: `clockInTime`, `clockOutTime`, `userId`（オーナー差し替え）
- ステータス遷移済み（APPROVED 等）の編集をどう扱うかをポリシー化:
  - ADMIN は強制編集可、STAFF は DRAFT のみ編集可、など。

### 3. シフトテンプレート (Shift Template)

「平日 9-18」「土日 10-22」のような _時間帯テンプレ_ を保存しておき、bulk 作成や個別作成のプリセットとして使う。

- 新エンティティ `ShiftTemplate { id, name, clockInLocalTime, clockOutLocalTime, daysOfWeek, role/タグ }`
- `POST /api/shifts/bulk/from-template { templateId, userId, startDate, endDate }`
- UI 側のシフト入力が劇的に楽になる。

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
