node {
  stage("Checkout") {
    checkout scm
  }
  stage("Build") {
    withMaven {
      sh "mvn clean install"
    }
  }
}
