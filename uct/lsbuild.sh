#! /bin/sh

LS=/usr/local/src/lecturesight
BUILDDIR=$LS/build

rm -rf $LS/deploy/*
cp -R $LS/lsuct/runtime/* $LS/deploy/

echo "Building UCT Lecturesight\n"
java -version
echo

cd $LS/lsuct

git status --short
echo

BUILDTIME=`date +%y%m%d-%H%M`
LASTCOMMIT=`git log --pretty=format:"%h" --max-count=1`
COMMITDATE=`git log --pretty=format:"%cd" --max-count=1`

cd $LS/lsuct/src
mvn clean install -DdeployTo=$LS/deploy/bundles/application

cd $LS/deploy

# Record version

echo "Built: $BUILDTIME " > build-info
echo "Last commit: $LASTCOMMIT" >> build-info
echo "Commit date: $COMMITDATE" >> build-info

tar zcf $BUILDDIR/lsuct-$BUILDTIME-$LASTCOMMIT.tgz *
rm $BUILDDIR/lsuct-latest.tgz
ln -s $BUILDDIR/lsuct-$BUILDTIME-$LASTCOMMIT.tgz $BUILDDIR/lsuct-latest.tgz

echo Latest build: lsuct-$BUILDTIME-$LASTCOMMIT.tgz
echo
