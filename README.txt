
**************************************************************************************
What is Winstone ?

**************************************************************************************

Winstone is a servlet container that was written out of a desire to provide servlet functionality without the bloat that full J2EE compliance introduces.

It is not intended to be a completely fully functional J2EE style servlet container (by this I mean supporting extraneous APIs unrelated to Servlets, such as JNDI, JavaMail, EJBs, etc) - this is left to Tomcat, Jetty, Resin, JRun, Weblogic et al.

Sometimes you want just a simple servlet container - without all the other junk - that just goes.

This is where Winstone is best suited.

You could find some documentation and support on this site:

 http://code.google.com/p/winstone/

 http://intelligents-ia.com/index.php/category/technique/Winstone

**************************************************************************************
Compiling Winstone

**************************************************************************************
Winstone, with the added patches for our Aspect Oriented Programming course at the University of Twente
can be compiled using maven.

mvn clean package

Compiles and packages maven. This yields the needed jar files in the */target/ directories.
When a test fails, try the following commands to compile:

mvn package -DskipTests
mvn package -fn

fn: fail never

**************************************************************************************
Running Winstone

**************************************************************************************
Winstone can be run in multiple ways. They are desceribed at <https://code.google.com/p/winstone/wiki/UsingWinstone>

The most simple method is:
java -jar target/winstone-X.X.X-boot.jar --webroot=<root dir>


**************************************************************************************
The original goals in writing Winstone were:

Supply fast, reliable servlet container functionality for a single webapp per server
Keep the size of the core distribution jar as low as possible
Keep configuration files to an absolute minimum, using command line options to optionally override sensible compiled in defaults.
Optionally support JSP compilation using Apache's Jasper. (http://jakarta.apache.org)

