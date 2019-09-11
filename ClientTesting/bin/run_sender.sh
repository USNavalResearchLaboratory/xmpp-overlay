
DIST_DIR=build/distributions
if [ -d $DIST_DIR/ClientTesting ]; then
    rm -rf $DIST_DIR/ClientTesting
fi
cd $DIST_DIR
unzip ClientTesting.zip
cd ClientTesting

./bin/ClientTesting $@
