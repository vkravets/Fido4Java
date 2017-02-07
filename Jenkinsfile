node {
  stage("Checkout") {
    checkout scm
  }
  withMaven {
    stage("Build") {
      sh "mvn clean install -Dmaven.test.skip=true"
    }
    stage("Test") {
      sh "mvn test"
    }
  }
}
