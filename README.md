# dati-frontendserver
<br />
<a href="https://travis-ci.com/italia/dati-frontendserver">
<img title="Build Status Images" src="https://api.travis-ci.com/italia/dati-frontendserver.svg?token=sdc8mJz3EyP3LyxtXjxQ">
</a>

Si tratta di un progetto playframework 2.5.13 basato su scala 2.11.8 e utilizza sbt 0.13.* per fare la build.

```
ssh-add ~/.ssh/YOUR_GITHUB_KEY
git clone https://github.com/italia/dati-frontendserver
sbt run
http://localhost:9000
```

Il progetto al momento e' completamente vuoto. Puoi provare a fare un clone e fare sbt run per vedere se funziona anche a te. 

Il progetto e' collegato a travi ci, un tool che ogni volta viene fatta una push sulla repo github lancia il processo di test e build in cloud indipendente dal tuo ambiente di sviluppo. Puoi provare a collegarti qua per vedere se riesci a vedere  la build del primo commit.

