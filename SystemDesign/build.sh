#!/bin/sh

# Build zip
VERSION=$(fgrep '<version>' pom.xml | head -n 1 | sed 's/.*<version>//;s=</version.*==')
ZIP=systemdesign-${VERSION}.zip 
mvn clean
mvn install
rm -vf "../${ZIP}"
(cd target
	mv SystemDesign-*.jar SystemDesign.jar
	zip -r ../../${ZIP} SystemDesign.jar lib
)
(cd ../Documentation
	zip -r ../${ZIP} *
)

# Build debian package
dpkg-buildpackage -us -uc -b
mv pom.xml.save pom.xml

