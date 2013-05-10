LIBPREFIX=../../com.amd.aparapi.jni/dist/libaparapi_$(uname -m)
case $(uname -s) in 
  Darwin) LIBNAME=${LIBPREFIX}.dyLib;;
  Linux)  LIBNAME=${LIBPREFIX}.so;;
esac
java\
 -agentpath:${LIBNAME}\
 -Dcom.amd.aparapi.executionMode=%1 \
 -classpath ../../com.amd.aparapi/dist/aparapi.jar:squares.jar \
 com.amd.aparapi.sample.squares.Main