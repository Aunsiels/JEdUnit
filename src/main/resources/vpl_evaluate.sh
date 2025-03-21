#! /bin/bash
rm -f *.class
javac -encoding UTF-8 -proc:none -cp ".:*" Checks.java
java  -jar checkstyle.jar -c style_checks.xml *.java > checkstyle.log

cat > vpl_execution <<EEOOFF
#! /bin/bash
java  -cp ".:*" Checks private
EEOOFF

chmod +x vpl_execution
