#!/bin/sh

# Build zip
VERSION=$(fgrep '<version>' pom.xml | head -n 1 | sed 's/.*<version>//;s=</version.*==')
ZIP=systemdesign-${VERSION}.zip 
mvn clean
mvn install
rm -vf "../${ZIP}"
(cd ../Application/target
	mv SystemDesign-*.jar SystemDesign.jar
	zip -r ../../${ZIP} SystemDesign.jar lib
)
(cd ../Documentation
	zip -r ../${ZIP} *
)

# Build debian package
dpkg-buildpackage -us -uc -b
for ii in ../*; do
	if [ -f "$ii/pom.xml.save" ]; then
		mv -v "$ii/pom.xml.save" "$ii/pom.xml"
	fi
done

