# Backend TODO / Known Pitfalls

このファイルは、現在のバックエンド実装で「動いてはいるが将来踏みそうな地雷」を
忘れないように記録するメモです。優先度の高いものから順に着手してください。

---

## 🔐 Auth / Token 周り

- [ ] **`AuthResponse.token` という命名の整理**
      現状フィールド名は `token`（= access token）。フロント側の内部呼称は
      `accessToken` で、`AuthResponseDto` のワイヤー名も `token` に揃え直したばかり。
      もし将来 `accessToken` にリネームするなら、以下を一括で更新する必要がある:
  - `src/main/kotlin/com/example/shiftapp/dto/response/AuthResponse.kt`
  - `src/main/kotlin/com/example/shiftapp/service/AuthService.kt`（`AuthResponse(token = access, ...)`）
  - `src/main/kotlin/com/example/shiftapp/config/OpenApiConfig.kt`（説明文）
  - `src/test/kotlin/com/example/shiftapp/controller/AuthControllerIntegrationTest.kt`
  - `src/test/kotlin/com/example/shiftapp/controller/ShiftControllerIntegrationTest.kt`
  - `src/test/kotlin/com/example/shiftapp/controller/ShiftRequestControllerIntegrationTest.kt`
  - フロント `shiftapp_client/src/lib/api/types.ts` の `AuthResponseDto.token`

- [ ] **`/api/auth/refresh` 失敗時のステータス統一**
      フロントの `apiFetch` は「401 が来たら 1 回だけ refresh、ダメなら anonymous」
      というルールで動いている。現状は `IllegalArgumentException` 起点で
      `GlobalExceptionHandler` 経由 → 4xx に変換されるが、明示的に **401** を返すか
      確認/統一しておくとフロント側の分岐が綺麗になる。

- [ ] **401 (未認証) と 403 (権限不足) の区別**
      Spring Security のデフォルトでは、`Authorization` ヘッダーを付けずに
      保護リソースを叩くと 403 が返る挙動になっている。
      `AuthenticationEntryPoint` を設定して「未認証 → 401」を返すようにしておくと、
      フロントの自動リフレッシュロジックがそのまま動くようになる。
      （今回の 403 の遠因はフィールド名不一致だったが、ヘッダー欠落時の振る舞いも
      同時に直しておくと予期せぬ 403 を防げる）

- [ ] **refresh token の `jti` 管理（reuse detection）**
      `JwtUtil.kt` のコメントにも書かれている通り、現在は署名 + `type=refresh` のみで
      検証しており、サーバ側に `jti` ストア（Redis or DB）を持っていない。
      盗難されたリフレッシュトークンの再利用検知をするなら以下が必要:
  - `refresh_tokens` テーブル（または Redis キー）
  - `JwtUtil.generateRefreshToken` で `jti` を発行 → 永続化
  - `AuthService.refresh` で `jti` を検証 → 旧 `jti` を破棄、新 `jti` を発行
  - 同じ `jti` が二度使われたら **そのユーザーの全 refresh を無効化**（reuse 検知）

- [ ] **CORS + credentials 設定（直接叩き構成にする場合）**
      現状は Next.js rewrite 越しを前提に `SecurityConfig` に CORS 設定なし。
      フロントが `NEXT_PUBLIC_API_BASE_URL` を立ててブラウザから直叩きする構成に
      した瞬間に Cookie が付かない／CORS で弾かれる問題が発生する。
  - `CorsConfigurationSource` Bean を追加
  - `allowCredentials = true`、`allowedOrigins` をワイルドカード不可で列挙
  - `http.cors {}` を有効化

- [ ] **本番 Cookie 属性の切り替え**
      `application.properties` の `app.auth.refresh-cookie.secure=false` を
      本番（HTTPS）では `true` に。クロスサイト SPA 構成にするなら
      `same-site=None` ＋ `secure=true` の組合せが必須。

---

## 🛡 Security / Authorization

- [ ] **`CreateShiftRequest.userId` をクライアントから受け取っている**
      `ShiftController.createShift` のコメントにも明記されている通り、本来は JWT の
      `subject` / `userId` claim から取得すべき。今のままだと STAFF が他人の
      `userId` を指定して勝手にシフトを作れてしまう。
  - JWT から `userId` を取り出すユーティリティを `JwtAuthenticationFilter` で
    `Authentication.principal` などに詰めておく
  - `@AuthenticationPrincipal` か `Authentication` 経由で controller が読む
  - `CreateShiftRequest` から `userId` を削除（または無視して上書き）

- [ ] **`ShiftRequestController` の `X-User-Id` ヘッダー依存**
      同上。`requesterId` をヘッダーから受けるのを廃止し、JWT 経由に統一。
      フロント側の `withUserIdHeader` オプションも将来的に不要になる。

- [ ] **`ShiftController` 既存エンドポイントの所有者チェック**
      `submitShift / approveShift / rejectShift` などで「自分のシフトかどうか」の
      検証が薄い／無い箇所がないか棚卸し。

---

## 📦 Endpoint coverage

フロント `lib/api/shifts.ts` のコメントに記載されている未実装エンドポイント:

- [x] `GET /api/shifts`（admin overview / 全件一覧）
      ADMIN-only `@PreAuthorize("hasRole('ADMIN')")`、クエリで `status` / `userId`
      の絞り込みと `page` / `size` / `sort` のページング対応済み。レスポンスは
      `PageResponse<ShiftResponse>` envelope。フロントは `listAllShifts` /
      `useAllShifts` から利用。
- [ ] `PUT /api/shifts/{id}`（時刻・所有者の編集）
- [ ] `DELETE /api/shifts/{id}`（削除）

---

## 🧹 Misc / Cleanup

- [ ] `application.properties` の `jwt.expiration`（legacy エイリアス）の段階的削除。
      `jwt.access.expiration` に一本化し、`build.gradle.kts` / 起動スクリプト等で
      使っていないことを確認してから消す。
- [ ] `jwt.secret` の環境変数化。
      現在 `application.properties` にハードコードされている（コメントに警告あり）。
      本番投入前に `JWT_SECRET` 環境変数 + `${JWT_SECRET}` プレースホルダ参照へ。
- [ ] `JwtAuthenticationFilter` の `try/catch` で例外を握りつぶしている件。
      現在は token 不正でも 200 系で処理が進み、Authorization ヘッダー無しと
      同じ扱いになる。意図的だがログ運用の観点で見直し余地あり。

---

## 📜 履歴メモ

- 2026-04-26: フロントとの DTO 名不一致（`token` vs `accessToken`）により
  ログイン後の `/api/shifts` POST が 403 になる問題を発見・修正。
  → フロント側を `token` に揃える形で対応済み。
  → 同時に「未認証時 401 を返す」「`userId` を JWT から取る」など、
  関連する設計上の宿題をこのファイルに集約。
