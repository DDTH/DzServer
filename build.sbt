name := "DzServer"

version := "0.0.1.3-R1"

libraryDependencies ++= Seq(
  "org.apache.felix"   % "org.apache.felix.framework" % "[4.2.1,5.0.0)",
  "org.apache.felix"   % "org.apache.felix.main"      % "[4.2.1,5.0.0)",
  "commons-io"         % "commons-io"                 % "[2.4,3.0)",
  "org.apache.commons" % "commons-lang3"              % "[3.1,4.0)",
  "com.github.ddth"    % "osgi-bundle-frontapi"       % "[0.1.3,0.1.4)",
  filters,
  javaJdbc,
  javaEbean,
  cache
)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sonatype releases repository" at "http://oss.sonatype.org/content/repositories/releases/"

play.Project.playJavaSettings
