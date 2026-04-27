# Cheat Sheet

## MockK

```kotlin
// 固定値を返す
every { mock.foo(any()) } returns 42

// 引数依存の動的な値を返す（ECHO）
every { mock.save(any()) } answers { firstArg() }

// 例外を投げる
every { mock.foo(any()) } throws RuntimeException("boom")

// 何回も呼ばれて毎回違う値を返す
every { mock.next() } returnsMany listOf(1, 2, 3)
```

---

## データベースの中を確認する方法 (PostgreSQL)

このプロジェクトでは、`docker-compose.yml` で PostgreSQL コンテナを動かしている。

### 接続情報（`docker-compose.yml` / `application.properties` より）

| 項目           | 値                  |
| -------------- | ------------------- |
| Host           | `localhost`         |
| Port           | `5432`              |
| Database       | `shiftapp_dev`      |
| User           | `shiftapp_user`     |
| Password       | `shiftapp_pass`     |
| Container name | `shiftapp_postgres` |

> ⚠️ `spring.jpa.hibernate.ddl-auto=create-drop` なので、Spring Boot アプリを起動するたびにテーブルが作り直される。確認したいデータがあるなら、アプリを止める前に覗くこと。

---

### 方法 1: Docker コンテナの中の `psql` を使う（最も手軽）

```bash
# コンテナが動いているか確認
docker ps

# コンテナ内の psql に入る
docker exec -it shiftapp_postgres psql -U shiftapp_user -d shiftapp_dev
```

`psql` 内でよく使うコマンド:

```sql
\dt                       -- テーブル一覧
\d users                  -- users テーブルの定義（カラム・制約・インデックス）
\d+ shifts                -- 詳細版（サイズや説明も）
\dn                       -- スキーマ一覧
\du                       -- ユーザー（ロール）一覧
\l                        -- データベース一覧
\x                        -- 表示を縦展開トグル（横に長いテーブル向け）
\q                        -- 終了

SELECT * FROM users;
SELECT * FROM shifts ORDER BY start_time DESC LIMIT 10;
SELECT * FROM shift_requests WHERE status = 'PENDING';
```

---

### 方法 2: ホスト側の `psql` から直接つなぐ

ホスト OS（Mac）に `psql` が入っていれば、コンテナに入らずに接続できる。

```bash
psql -h localhost -p 5432 -U shiftapp_user -d shiftapp_dev
# パスワードを聞かれたら: shiftapp_pass
```

毎回聞かれるのが面倒なら `PGPASSWORD` を渡す:

```bash
PGPASSWORD=shiftapp_pass psql -h localhost -U shiftapp_user -d shiftapp_dev
```

---

### 方法 3: ワンライナーで SQL を実行する

スクリプトや確認だけしたい時に便利。

```bash
# コンテナ経由
docker exec -it shiftapp_postgres \
  psql -U shiftapp_user -d shiftapp_dev -c "SELECT id, email, role FROM users;"

# ホスト経由
PGPASSWORD=shiftapp_pass psql -h localhost -U shiftapp_user -d shiftapp_dev \
  -c "SELECT count(*) FROM shifts;"
```

---

### 方法 4: GUI ツールから接続する

以下のような GUI を使うと、テーブルをクリックで覗けて楽。

- **DBeaver**（無料・全部入り）
- **TablePlus**（Mac で人気）
- **pgAdmin**（PostgreSQL 純正）
- **IntelliJ IDEA / DataGrip の Database ツール**（IDE 派におすすめ）

接続設定は上の表の値をそのまま入れるだけ。

---

### 方法 5: テスト時のデータベース

`src/test/resources/application-test.properties` を使う統合テストでは、別の DB（多くは H2 など）が使われることがある。テストで何が入っているかを見たい場合は、テスト用の設定を確認すること。

```bash
cat src/test/resources/application-test.properties
```

---

### よく使う調査クエリ（PostgreSQL）

```sql
-- 現在のDB / ユーザー / バージョン
SELECT current_database(), current_user, version();

-- テーブルごとの行数（ざっくり）
SELECT relname AS table_name, n_live_tup AS row_count
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;

-- 特定テーブルのカラム情報
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'shifts';

-- 外部キーを調べる
SELECT conname, conrelid::regclass AS table, confrelid::regclass AS references
FROM pg_constraint
WHERE contype = 'f';
```

---

### トラブルシューティング

| 症状                             | 対処                                                                                                  |
| -------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `could not connect to server`    | `docker compose up -d` で Postgres が起動しているか確認                                               |
| `password authentication failed` | ユーザー名 / パスワードを確認（`shiftapp_user` / `shiftapp_pass`）                                    |
| 起動するたびにテーブルが空       | `ddl-auto=create-drop` のせい。`update` や `none` に変える、または Spring Boot を止めずに psql で覗く |
| ポート `5432` が使われている     | ホストの別 Postgres を止めるか、`docker-compose.yml` のポートマッピングを変える                       |

---
