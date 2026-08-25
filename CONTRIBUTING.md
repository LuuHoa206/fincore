# Contributing

Read `docs/GIT_WORKFLOW.md` before creating a branch.

Before opening a pull request:

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run lint
npm run build
```

Never commit real passwords, tokens, personal financial data, database dumps,
or production environment files.
