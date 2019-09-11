the components in this directory depend on ../lib and ../dist/xop.jar 
so please do not move the components around.

- dnguyen (dn53@drexel.edu) 2011-05-12

Components need to have the following:

- build.xml file that implements the following targets: compile, dist, deploy, 
  undeploy, clean. These targets are called by the master build.xml in the XOP dir.
- src/component.xml to specify the subdomain and name of the component
- (optional) .project and .classpath to load project into Eclipse
