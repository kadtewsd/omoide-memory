---

# omoide-memory-uploader

スマートフォンの中に眠っている大切な「思い出（写真・動画）」を、あなたの個人用 Google ドライブへ安全にバックアップするための Android アプリです。

## 1. なぜこのアプリが必要なのか（背景）

現代のスマートフォンは高画質な写真や動画を大量に生成しますが、それらは常に「紛失」や「故障」のリスクにさらされています。
市販のクラウドフォトサービスは便利ですが、**自分のデータは、自分の管理下にあるストレージに、自分が納得するタイミングで保管したい**というプライバシーとコントロールを重視するエンジニア・ユーザーのためにこのアプリは誕生しました。

## 2. 何をやるのか（機能）

* **未アップロードの自動検知**: スマートフォン内のメディア（MediaStore）とアプリ内データベースを照合し、まだバックアップしていないファイルを自動で見つけ出します。
* **手動・選択アップロード**: ユーザーが自分で写真を選んで、即座にアップロードを開始できます。
* **賢い自動バックアップ**: 「Wi-Fi 接続時のみ」「バッテリー残量が十分な時のみ」といった条件下で、バックグラウンドで静かにバックアップを遂行します。
* **進捗の可視化**: 今どれだけのファイルがあり、どれだけ完了したのかをリアルタイムで確認できます。

## 3. どうやってやるのか（技術構成）

Android の最新技術スタックを用いて、サーバーサイドエンジニアも納得する「堅牢な設計」を採用しています。

* **UI (Jetpack Compose)**: 状態（State）に基づいた宣言的な UI 構築。
* **非同期制御 (Coroutines & Flow)**: メインスレッドを止めない非同期処理と、リアクティブなデータストリーム。
* **バックグラウンド処理 (WorkManager)**: OS がアプリを終了させても、タスクが確実に完了することを保証します。
* **エラーハンドリング (Either 型)**: ネットワークエラーなどを例外（Exception）として投げっぱなしにせず、値として厳格に扱います。

---

## 4. セットアップ手順（Google Cloud Platform）

Google ドライブへアクセスするために、Google Cloud プロジェクトの作成と設定が必要です。

### 手順 A: Google Cloud プロジェクトの作成

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセスします。
2. 「新しいプロジェクト」を作成し、任意の名前（例: `omoide-uploader`）を付けます。
3. メニューの「API とサービス」 > 「ライブラリ」から **Google Drive API** を検索し、「有効にする」をクリックします。

### 手順 B: OAuth 同意画面の設定

1. 「API とサービス」 > 「OAuth 同意画面」を開きます。「クライアント」をクリックします。
2. User Type は「外部」を選択（個人利用でもこちらで OK です）。
3. **アプリ情報** を入力します。
4. 「データアクセス」をクリックして、**スコープを追加または削除** に `https://www.googleapis.com/auth/drive.file`（アプリが作成したファイルへのアクセス）を追加します。非機密のスコープに API が追加されて同意画面が出ます。
5. **重要: テストユーザー** の項目に、アップロードに使用する自分の Google メールアドレスを追加してください（アプリが「公開」ステータスになるまで、ここで登録した人しか使えません）。
  5.1 「検証センター」をクリック
  5.2 「オーディエンス構成を表示」をクリック
  5.3 テストユーザーを追加

### 手順 C: クライアント ID の発行
この手順をすることで、Google アカウントの認証のコードが動作します。この手順がないと、下記が常に 0 (Canceled) になってアカウント情報同期できたことになりません。
```kotlin
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) { // here is always 0 if you don't put sha1 on google console.
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            viewModel.handleSignInResult(task, onSignInSuccess)
        }
    }
```
debug 用と release 用を作成します。

1. 「認証情報」 > 「認証情報を作成」 > 「OAuth クライアント ID」を選択します。
2. **Android 用**:
   * 開発用パソコンの SHA-1 証明書指紋を入力します
   * パッケージ名を入力。

2.1. debug

- sha1
./gradlew signingReport` で確認可能です。

- パッケージ名
下記が build.gradle.kts に記述があるので、<package.name>.debug と配置します。
例 : com.kasakaid.omoidememory.debug

```kts
    buildTypes {
        debug {
            // パッケージ名の末尾に .debug をつける (例: com.example.app.debug)
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

```

2.2 release

- sha1
まず、jks ファイルを下記で発生させる。

```bash
keytool -genkey -v -keystore omoide-<account_name>.jks -keyalg RSA -keysize 2048 -validity 10000 -alias <なんらかのエイリアス>
```

-keystore の <account_name> は、複数アカウントで jks を使い分けて別アプリとできる様、アイテムを保存するドライブのアカウントの名前にしています。
わかりやすければなんでも良いです。

作成したら、下記で sha1 を確認

keytool -keystore omoide-<account_name>.jks -list -v

- パッケージ名
debug の .debug を外したもので OK

例 : com.kasakaid.omoidememory

---

### 手順 D: API 有効化の最終確認（よくある落とし穴）

GCPのコンソールで、**「Google Drive API」が本当に「有効」になっているか**を再確認してください。クライアントIDがあっても、ライブラリ自体が有効化されていないと、API呼び出し時に `403 Forbidden` または `Google Drive API has not been used in project XXX before` というエラーが返されます。


1. [Google Cloud Console API ライブラリ](https://console.cloud.google.com/apis/library) へアクセスします。
2. 対象のプロジェクトの Google Drive API を「有効にする」をクリックします。
3. [API ライブラリ](https://console.cloud.google.com/apis/library) へ行く。
4. 「Google Drive API」を検索。
5. ステータスが「管理」または「有効」になっていればOKです。

---

3. **Web アプリケーション用**:
* Android の Google Sign-In 実装では、サーバー側の ID としてこれが必要になる場合があります。名前を付けて作成し、発行された「クライアント ID」を控えておきます。

---

## 5. ビルド前の設定

### local.properties の設定

プロジェクトルートに `local.properties` を作成し、sdk.dir をセット
```properties
sdk.dir=/Users/<user_name>/Library/Android/sdk
```

- `sdk.dir`: Android SDKのパス（Android Studioが自動生成）

**フォルダIDの取得方法:**
1. Google Driveでフォルダを開く
2. URLの末尾がフォルダID
    - 例: `https://drive.google.com/drive/folders/1a2b3c4d5e` → ID は `1a2b3c4d5e`
3. 上記の例のように、取得したIDを `omoide.folderId` に指定してください

## 6. アプリの使いかた

1. アプリを起動し、Google アカウントでログインします。
2. **手動アップロード**: メイン画面の「選択してUP」から好きな写真を選んで実行します。
3. **自動アップロード**: 設定画面（またはトグル）で自動実行を ON にします。
* 1時間おきに実行を試みますが、**Wi-Fi 接続時** かつ **充電中（またはバッテリー十分）** の時だけ動作するよう OS が賢く制御します。


---


### アプリのアイコン（ランチャーアイコン）を作る手順

#### 1. Image Asset Studio を起動する

1. Android Studio の左側にあるプロジェクトツリーで、**`app` フォルダを右クリック**します。
2. **New > Image Asset** を選択します。

#### 2. アイコンの設定（Configure Image Asset）

「Asset Studio」という画面が開くので、以下の項目を設定します。

* **Icon Type**: `Launcher Icons (Adaptive and Legacy)` を選択。
* **Name**: `ic_launcher` のままでOKです。
* **Foreground Layer（前面レイヤー）**:
* `Asset Type` を `Image` にし、`Path` からあなたの用意した画像（ロゴなど）を選びます。
* **Scaling**: `Resize` スライダーを動かして、画像がセーフティゾーン（黒い円の内側）に綺麗に収まるよう調整します。


* **Background Layer（背面レイヤー）**:
* `Asset Type` を `Color` にして好きな背景色を選ぶか、背景用の画像を指定します。



#### 3. 生成と保存

1. **Next** をクリック。
2. 保存先が `src/main/res` になっていることを確認して **Finish**。
* これで、解像度ごとのフォルダ（`mipmap-hdpi` や `mipmap-xxxhdpi` など）に適切なサイズの画像が自動で書き出されます。



> **作成者より**
> このアプリで大切な思い出を、あなたの手で安全に守りましょう。
