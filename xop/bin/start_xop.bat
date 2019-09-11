set options=%*
echo "Running XOP with options [[ %options% ]]"
echo %JAVA_HOME%
PATH=%PATH%;.\jniLibs\x86_64
java -cp ".\xop-all.jar" -Djava.library.path="%JAVA_HOME%\jre\lib\ext;.\jniLibs\x86_64" %options% edu.drexel.xop.Run

