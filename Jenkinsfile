node {
  stage("Checkout") {
    checkout scm
  }
  withMaven {
    stage("Build") {
      sh "mvn clean install -Dmaven.test.skip=true"
    }
    stage("Test") {
      withEnv(['RPC_BIND_IP=$OPENSHIFT_JENKINS_IP']) {
        sh "mvn test"
      }
    }
  }
}
