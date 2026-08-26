# Kien truc FinCore

## Tong quan

FinCore la modular monolith: mot ung dung Spring Boot, mot co so du lieu
PostgreSQL, va cac module duoc tach theo nghiep vu. Cach nay giu giao dich tai
chinh nhat quan trong mot database transaction, nhung van dat ranh gioi ro rang
de tach thanh service rieng khi can.

```text
React Web
   |
REST API
   |
Spring Boot
   |-- identity
   |-- wallet
   |-- transaction
   |-- moneyjar
   |-- budget
   |-- savinggoal
   |-- reporting
   |-- audit
   `-- shared
   |
PostgreSQL
```

## Quy uoc 3 lop MVC

Ma nguon duoc to chuc **theo module nghiep vu**, nhung moi module bat buoc tuan
thu 3 lop. Vi du trong `wallet` va `transaction`:

```text
HTTP request
  -> Controller (api layer)
  -> Service (business layer)
  -> Repository (data-access layer)
  -> PostgreSQL
```

| Lop | Trach nhiem | Khong duoc lam |
| --- | --- | --- |
| Controller | Dinh tuyen API, nhan DTO, validate, lay nguoi dung tu JWT, tra HTTP response | Chua quy tac nghiep vu hoac goi repository truc tiep |
| Service | Xu ly use case, kiem tra quyen so huu, ap dung quy tac tien, quan ly transaction | Phu thuoc vao HTTP request/response |
| Repository | Truy van JPA, lock ban ghi, luu entity | Chua nghiep vu, validate hay tinh toan tai chinh |
| Entity/Model | Anh xa du lieu va bao ve bat bien noi tai | Tra truc tiep qua API |
| DTO | Hop dong input/output cua API | Chua logic persistence |

Vi du luong `POST /api/v1/transactions`:

```text
TransactionController
  -> CreateTransactionRequest DTO
  -> TransactionService.create(...)
  -> WalletRepository.findOwnedForUpdate(...)
  -> FinancialTransactionRepository.save(...)
  -> LedgerEntryRepository.saveAll(...)
  -> TransactionResponse DTO
```

`ThreeLayerArchitectureTest` bao ve quy uoc nay: controller nghiep vu phai
phu thuoc vao service, khong duoc inject repository.

## Quy tac du lieu va transaction

- Tat ca thay doi so du vi duoc thuc hien trong `@Transactional` service.
- Thu/chi tao mot giao dich va hai but toan so cai can bang trong cung transaction.
- Vi khong cho phep am se tu choi khoan chi vuot so du truoc khi ghi du lieu.
- Giao dich da ghi nhan khong bi xoa; hoan tac tao giao dich doi ung de giu lich su doi soat.
- API nhan `Idempotency-Key` de mot lan gui lai cung request khong ghi nhan lai giao dich.
- DTO API khong tra entity JPA, giup tranh lo ro du lieu noi bo va lazy-loading khong kiem soat.

## Quy tac lien module

- Module so huu entity va repository cua chinh no.
- Service duoc phep goi public service cua module khac khi can; khong truy cap truc tiep repository noi bo cua module khac.
- Khi luong nghiep vu lon hon, uu tien domain event/outbox cho audit, thong bao va reporting thay vi lam controller phinh to.

## Huong mo rong

- Redis cho cache dashboard, rate-limit va idempotency phan tan.
- Message broker cho email, notification va reporting projection.
- Object storage cho hoa don/chung tu.
- OpenTelemetry cho log, metric va trace.
- AI chi goi y phan loai hoac tom tat; khong tu dong thay doi giao dich tai chinh.
