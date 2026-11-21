# VietBus Card – Hệ thống thẻ xe buýt thông minh ứng dụng Java Card

Java Card applet (chạy trên JCardSim) + Desktop Swing (máy bán vé, máy quẹt trên xe, Admin) + Backend Spring Boot/PostgreSQL.

## Cấu trúc

| Module | Nội dung |
|---|---|
| `card-applet` | `BusCardApplet` — Java Card 3.0.5 classic (chỉ byte/short) |
| `common` | Giao thức phía host: `BusCardClient`, `CardInfo`, `TxRecord`, `PassengerProfile`, RSA helpers |
| `backend` | *(giai đoạn 3)* Spring Boot REST + PostgreSQL |
| `desktop` | *(giai đoạn 4)* Swing + JCardSim |

## Yêu cầu

- JDK 21, Maven 3.9+
- Docker (cho PostgreSQL): `docker compose up -d`
- **jcardsim 3.0.5-SNAPSHOT** trong `~/.m2` (không có trên Maven Central — bản Central 2.2.2 quá cũ):

```bash
git clone --depth 1 https://github.com/licel/jcardsim.git
git clone --depth 1 https://github.com/martinpaljak/oracle_javacard_sdks.git
cd jcardsim
JC_CLASSIC_HOME=$PWD/../oracle_javacard_sdks/jc305u3_kit mvn install -DskipTests -Djava.version=1.8
```

Applet được biên dịch với Java Card 3.0.5 API của Oracle SDK. File này không được đưa lên git, hãy tự chép vào `lib/`:

```bash
mkdir -p lib && cp ../oracle_javacard_sdks/jc305u3_kit/lib/api_classic.jar lib/
```

## Build & test

```bash
mvn -pl common,card-applet test
```

## Giao thức APDU (CLA = 0x80)

| INS | Lệnh | Điều kiện |
|---|---|---|
| 10 | GET_INFO — 52 byte thông tin công khai | — |
| 11 | GET_PUBLIC_KEY | đã cá nhân hóa |
| 12 | INTERNAL_AUTH — ký challenge ‖ cardId ‖ txCounter | đã cá nhân hóa |
| 13 | GET_CHALLENGE — cho 1 lệnh issuer | — |
| 20 / 21 / 22 | VERIFY_PIN / CHANGE_PIN / UNBLOCK_PIN | — / PIN / issuer |
| 30 / 31 | READ_PROFILE / UPDATE_PROFILE (AES) | PIN |
| 32 / 33 | READ_PHOTO / WRITE_PHOTO (khối 224 byte, AES) | PIN |
| 40 | TOP_UP | PIN + issuer |
| 41 / 42 | TAP_IN / TAP_OUT | thẻ ACTIVE |
| 43 | BUY_PASS | PIN |
| 44 | GET_LAST_TX | — |
| 50 / 51 | SET_STATUS / UPDATE_PARAMS | issuer |
| 60 / 61 | PERSONALIZE / ACTIVATE | INSTALLED / PERSONALIZED + issuer |

Lệnh **issuer**: thẻ phát challenge 16 byte; Backend ký `SHA256withRSA(challenge ‖ cardId ‖ INS ‖ payload)` bằng khóa riêng nhà phát hành; thẻ xác minh bằng khóa công khai nạp lúc cá nhân hóa. Challenge dùng một lần.

Mọi lệnh thay đổi số dư trả về bản ghi 40 byte + chữ ký RSA của thẻ:
`cardId(16) counter(4) type(1) amount(4) balanceAfter(4) route(2) station(2) dateTime(6) flags(1)`.

Status word riêng: `6A90` không đủ số dư · `6A91` thẻ hết hạn · `6A92` thẻ bị khóa/thu hồi · `6A93` chưa quẹt lên · `6A94` vé tháng sai tuyến · `6A95` vượt hạn mức · `6A96` sai chữ ký issuer · `63Cx` sai PIN còn x lần · `6983` PIN bị khóa.

## Ghi chú bảo mật

- Hồ sơ/ảnh mã hóa AES-128-CBC bằng DEK ngẫu nhiên; DEK được bọc bằng `KEK = SHA-256(PIN ‖ salt)[0..16]`. Đổi PIN chỉ bọc lại DEK. Admin reset PIN ⇒ DEK mới, hồ sơ cũ bị xóa và ghi lại từ Backend.
- Khóa riêng RSA của thẻ sinh trên thẻ, không có lệnh xuất.
- Thẻ không có đồng hồ: máy đọc gửi ngày giờ trong lệnh; Backend đối soát thời gian và bộ đếm giao dịch.
- RSA-1024 để chữ ký vừa một APDU ngắn; trên thẻ thật nên dùng RSA-2048 + extended APDU hoặc ECDSA P-256.
