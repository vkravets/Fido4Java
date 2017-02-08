node {
  stage("Checkout") {
    checkout scm
  }
  withMaven(mavenOpts: '-Xmx200m') {
    stage("Build") {
      sh "mvn clean install -Dmaven.test.skip=true"
    }
    stage("Test") {
      sh "mvn test"
    }
  }
}
