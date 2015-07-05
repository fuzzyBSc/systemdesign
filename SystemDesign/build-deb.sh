#!/bin/sh

dpkg-buildpackage -us -uc -b
mv pom.xml.save pom.xml

