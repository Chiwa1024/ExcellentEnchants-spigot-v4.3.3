## Note
https://maven.elmakers.com/repository/org/spigotmc/spigot/

```
mvn install:install-file -Dfile="spigot-1.21.7-R0.1-SNAPSHOT-remapped-mojang.jar" -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.21.7-R0.1-SNAPSHOT -Dpackaging=jar -Dclassifier=remapped-mojang

mvn install:install-file -Dfile="spigot-1.21.4-R0.1-SNAPSHOT-remapped-mojang.jar" -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.21.4-R0.1-SNAPSHOT -Dpackaging=jar -Dclassifier=remapped-mojang

mvn install:install-file -Dfile="spigot-1.21.3-R0.1-SNAPSHOT-remapped-mojang.jar" -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.21.3-R0.1-SNAPSHOT -Dpackaging=jar -Dclassifier=remapped-mojang

mvn install:install-file -Dfile="spigot-1.21.1-R0.1-SNAPSHOT-remapped-mojang.jar" -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.21.1-R0.1-SNAPSHOT -Dpackaging=jar -Dclassifier=remapped-mojang

mvn install:install-file -Dfile=ProtocolLib-753.jar -DgroupId=com.comphenix.protocol -DartifactId=ProtocolLib -Dversion=5.3.0-SNAPSHOT -Dpackaging=jar

mvn install:install-file -Dfile=nightcore-2.10.0.jar -DgroupId=su.nightexpress.nightcore -DartifactId=nightcore -Dversion=2.10.0 -Dpackaging=jar

cls && mvn clean install -U
```
