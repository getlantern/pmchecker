To Build:

```bash
gradle -Pplatform=mac -Parch=x86_64 shadowJar
```

or

```bash
gradle -Pplatform=win -Parch=x86 shadowJar
```

To run:

java -jar pmchecker-0.1.0-shadow-win.jar