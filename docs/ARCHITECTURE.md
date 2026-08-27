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
   |-- allocationrule
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
thu 3 lop. Vi du trong `wallet`, `moneyjar` va `transaction`:

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
  -> WalletService.requireOwnedWalletForTransaction(...)
  -> FinancialTransactionRepository.save(...)
  -> LedgerEntryRepository.saveAll(...)
  -> TransactionResponse DTO
```

`ThreeLayerArchitectureTest` bao ve quy uoc nay: controller nghiep vu phai
phu thuoc vao service, khong duoc inject repository.

Luong `GET /api/v1/reports/dashboard` cung tuan theo quy uoc nay. `DashboardController`
chi lay nguoi dung va tham so thang, `DashboardService` phoi hop cac public
service cua wallet, money jar, budget, saving goal va transaction. Cac tong thu
chi theo tien te duoc truy van boi `TransactionReportingService`; React chi
hien thi `DashboardResponse`, khong tu cong du lieu tren trinh duyet.

## Quy tac du lieu va transaction

- Tat ca thay doi so du vi duoc thuc hien trong `@Transactional` service.
- Thu/chi tao mot giao dich va hai but toan so cai can bang trong cung transaction.
- Chuyen tien noi bo tao mot `TRANSFER` va hai but toan vi doi ung, mot am tai
  vi nguon va mot duong tai vi dich. Hai vi phai cung tien te, duoc khoa theo
  thu tu ID truoc khi kiem tra so du va cap nhat; vi vay tong tai san khong doi
  va giam nguy co deadlock khi co hai lenh chuyen nguoc chieu cung luc.
- Vi khong cho phep am se tu choi khoan chi vuot so du truoc khi ghi du lieu.
- Giao dich da ghi nhan khong bi xoa; hoan tac tao giao dich doi ung de giu lich su doi soat.
- API nhan `Idempotency-Key` de mot lan gui lai cung request khong ghi nhan lai giao dich.
- DTO API khong tra entity JPA, giup tranh lo ro du lieu noi bo va lazy-loading khong kiem soat.
- Hu tien la phan bo theo muc dich, khong phai mot vi tien rieng. Khi phan bo,
  `MoneyJarService` lock danh sach vi va hu cua nguoi dung, dam bao tong tien
  da gan cho cac hu khong vuot qua so du vi thuc te cung loai tien.
- Phan bo va giai phong hu chi tao `jar_movements`, khong lam thay doi so du
  vi. Vi vay tong tai san khong bi dem hai lan.
- Quy tac chia tien thuoc module `allocationrule`, co controller, service va
  repository rieng. Moi quy tac gan voi mot tien te va co toi da mot quy tac
  dang bat cho moi nguoi dung/tien te. Khi nguoi dung chu dong bat tuy chon
  tu chia tren khoan `INCOME`, `TransactionService` khoa vi, khoa hu, khoa quy
  tac, ghi giao dich va but toan hu trong cung mot database transaction.
- Ngan sach khong luu tru truong "da chi" de tranh sai lech du lieu. `BudgetService`
  lay tong giao dich `EXPENSE` co trang thai `POSTED` trong dung thang, dung danh
  muc va dung loai tien thong qua `TransactionReportingService`.
- Muc tieu tiet kiem lay tien da tich luy tu `allocatedBalance` cua mot hu tien.
  `SavingGoalService` goi public service cua module `moneyjar`, khong truy cap
  truc tiep repository cua module nay.
- Dashboard tong hop so du vi, tien da phan bo, thu/chi theo tung loai tien,
  canh bao ngan sach va muc tieu dang mo. He thong khong cong chung cac tien te
  khac nhau khi chua co ty gia quy doi.
- Lich su giao dich loc va phan trang tai database; client chi gui dieu kien va
  trang can xem, khong tai toan bo lich su roi loc tai trinh duyet.

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
