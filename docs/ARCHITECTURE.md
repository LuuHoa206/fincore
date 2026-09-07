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
   |-- recurring
   |-- splitbill
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

Luong `POST /api/v1/transaction-drafts/suggestion` cung dung quy uoc nay.
`TransactionDraftAssistantController` chi xac thuc nguoi dung va nhan DTO;
`TransactionDraftAssistantService` doc timezone qua `AuthService`, phan tich
mot ghi chu ngan bang cac quy tac minh bach, sau do tai dung
`CategorySuggestionService` de goi y danh muc ma nguoi dung co the xem. Luong
nay khong co repository rieng, khong ghi entity va khong thay doi so du. Client
phai dua de xuat vao form giao dich thong thuong va nguoi dung van phai gui
request tao giao dich rieng co `Idempotency-Key`.

Luong `GET /api/v1/budgets/suggestions` la mot read-model tuong tu.
`BudgetController` chi xac thuc user va chuyen thang/tien te cho
`BudgetLimitSuggestionService`. Service doc danh muc chi co the su dung, loai
tru danh muc da co budget trong thang duoc chon va tong hop cac giao dich
`EXPENSE` da `POSTED` cua ba thang truoc qua public
`TransactionReportingService`. Ket qua la han muc trung binh theo tung loai
tien; khong co entity nao duoc ghi va client van phai goi `POST /budgets` de
nguoi dung xac nhan tao ngan sach.

Luong `GET /api/v1/reports/dashboard` cung tuan theo quy uoc nay. `DashboardController`
chi lay nguoi dung va tham so thang, `DashboardService` phoi hop cac public
service cua wallet, money jar, budget, saving goal va transaction. Cac tong thu
chi theo tien te duoc truy van boi `TransactionReportingService`; React chi
hien thi `DashboardResponse`, khong tu cong du lieu tren trinh duyet.

Luong `GET /api/v1/reports/dashboard/insights` cung chi doc. Controller chuyen
nguoi dung va ky bao cao cho `FinancialInsightService`; service nay dung
`DashboardService`, `BudgetService` va `SavingGoalService` de tao cac nhan xet
giai thich duoc tu du lieu da ghi nhan. No khong tu dong sua giao dich, ngan
sach hay muc tieu, va khong tu nhan la du bao tai chinh.

Luong `GET /api/v1/reports/dashboard/unusual-expenses` cung chi doc va giu
dung ranh gioi 3 layer. `DashboardController` chi chuyen user va ky bao cao cho
`ExpenseAnomalyDetectionService`. Service lay timezone cua user, goi public
`TransactionReportingService` de lay khoan chi thang hien tai va baseline ba
thang truoc theo danh muc/tien te. Controller khong inject repository, va
reporting module khong thay doi giao dich. Ket qua chi la goi y ra soat co ly do
cu the, khong phai ket luan gian lan.

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
- Goi y han muc ngan sach cung chi doc giao dich `EXPENSE` da `POSTED`. No
  khong bao gio tao budget tu dong, bo qua danh muc da co budget trong thang
  dich va khong tron cac giao dich khac tien te.
- Muc tieu tiet kiem lay tien da tich luy tu `allocatedBalance` cua mot hu tien.
  `SavingGoalService` goi public service cua module `moneyjar`, khong truy cap
  truc tiep repository cua module nay.
- Dashboard tong hop so du vi, tien da phan bo, thu/chi theo tung loai tien,
  canh bao ngan sach va muc tieu dang mo. He thong khong cong chung cac tien te
  khac nhau khi chua co ty gia quy doi.
- Lich su giao dich loc va phan trang tai database; client chi gui dieu kien va
  trang can xem, khong tai toan bo lich su roi loc tai trinh duyet.
- Xuat CSV van nam trong module `transaction`. `TransactionCsvExportService`
  ap dung cung bo loc va owner scope nhu lich su giao dich, doc ledger theo lo
  de lay ten vi thay vi truy van tung dong. Export la read-only, dung mui gio
  cua nguoi dung va tu choi request vuot 10.000 dong de tranh cat ngam du lieu.
- Module `statementimport` co controller va service rieng, nhung khong co
  repository hay bang luu file. `StatementImportService` parse file trong bo
  nho, lay vi va danh muc qua public service, roi goi `TransactionService` de
  ghi tung dong hop le. Preview va confirm deu kiem tra lai toan bo file; dong
  trung duoc nhan ra bang idempotency key on dinh va bo qua an toan. Module nay
  khong ghi truc tiep vao transaction, ledger, hay wallet repository.
- Module `reconciliation` co controller, service rieng. Preview van la
  read-only; khi nguoi dung chu dong xac nhan dieu chinh, service chi kiem tra
  quyen so huu vi va mui gio roi goi public `TransactionService`.
  `WalletReconciliationService` lay quyen so huu vi qua `WalletService`, mui
  gio qua `AuthService`, va tong but toan lich su qua public
  `WalletLedgerReportingService` cua module transaction. No khong co repository
  rieng va khong tu cap nhat so du vi; `TransactionService` khoa user/vi, tinh
  lai chenh lech, ghi but toan kep va audit trong cung database transaction.
- Quy tac giao dich dinh ky thuoc module `recurring`, co controller, service va
  repository rieng. Scheduler chi goi public service cua module nay; khi can ghi
  giao dich, `RecurringRuleExecutionService` goi `TransactionService` thay vi
  ghi truc tiep vao bang giao dich hay so cai. Quy tac tu dong chi chay khi
  `autoRecord` duoc nguoi dung chu dong bat. Moi ky co idempotency key duoc tao
  tu ID quy tac va thoi diem da hen, dong thoi rule duoc khoa pessimistic trong
  giao dich rieng. Vi vay retry hoac nhieu scheduler khong the ghi trung ky.
- Tan suat thang luu ngay neo ban dau. Mot quy tac dat ngay 31 se dung ngay 28
  o thang Hai neu can, nhung se quay lai ngay 31 o thang Ba thay vi bi troi vinh
  vien thanh ngay 28.
- Chia hoa don thuoc module `splitbill`. `SplitBillService` khong ghi truc tiep
  vao `financial_transactions` hay `ledger_entries`: no goi `TransactionService`
  de ghi mot khoan chi ban dau va mot khoan thu cho moi lan hoan tien. Service
  khoa hoa don truoc khi ghi nhan hoan tien, kiem tra so tien con phai thu, luu
  lien ket giao dich va cap nhat trang thai trong cung mot database transaction.
  `TransactionReversalGuard` la extension point cua module transaction; module
  split bill dung no de chan hoan tac truc tiep giao dich da lien ket, tranh so
  cai va cong no bi lech nhau.
- Module `notification` chi tong hop nhac viec tu public service cua `recurring`
  va `budget`; no khong doc repository noi bo cua hai module nay. Bang
  `notification_read_states` chi luu tuy chon da doc theo nguoi dung va key cua
  canh bao hien tai. Doc mot thong bao khong the tao giao dich, thay doi ngan
  sach hay tu dong ghi nhan ky dinh ky.

## Quy tac lien module

- Module so huu entity va repository cua chinh no.
- Service duoc phep goi public service cua module khac khi can; khong truy cap truc tiep repository noi bo cua module khac.
- Audit log la module doc lap: service nghiep vu ghi log trong cung database transaction, con API chi tra ve log cua nguoi dung dang dang nhap.
- Khi luong nghiep vu lon hon, uu tien domain event/outbox cho audit, thong bao va reporting thay vi lam controller phinh to.

## Observability va van hanh

- `CorrelationIdFilter` chay truoc request processing, nhan hoac tao `X-Correlation-Id`, dua gia tri nay vao MDC va tra lai trong response. ID duoc xoa khoi MDC sau request de khong bi dung lai boi thread pool.
- Log request co cau truc `method`, `path`, `status` va `duration_ms`; khong ghi body, token, header xac thuc hay query parameter.
- Actuator chi expose `health`, `info` va `metrics`. Health probe la public de nen tang deploy kiem tra liveness/readiness; info va metrics can xac thuc.
- Huong dan endpoint, xu ly su co va quy tac bao ve du lieu van hanh duoc ghi trong `docs/OPERATIONS.md`.

## Huong mo rong

## Monthly review module

`monthlyreview` has its own controller, service, repository and
`monthly_reviews` table. It stores only a user's reflection and next-month
focus by month. `MonthlyReviewService` calls public reporting services for
cash-flow facts and insights, so no financial total is duplicated or mutable
from this module.

## Financial calendar module

`financialcalendar` is a read-only composition layer with its own controller
and service, but no database table. `FinancialCalendarService` asks public
services from Identity, Transaction and Recurring for the user's time zone,
bounded posted transactions and enabled schedules. It then groups the result by
local date. `ACTUAL` and `SCHEDULED` entries are separate so the client cannot
confuse a ledger fact with a future projection. The module never writes a
transaction, wallet, ledger entry, audit event, or recurring rule.

- Redis cho cache dashboard, rate-limit va idempotency phan tan.
- Message broker cho email, notification va reporting projection.
- Object storage cho hoa don/chung tu.
- OpenTelemetry cho log, metric va trace.
- AI chi goi y phan loai hoac tom tat; khong tu dong thay doi giao dich tai chinh.
