
node {
    try {
        timestamps {
            stage("Checkout") {
                checkout scm
            }
            withMaven {
                stage("Build") {
                    sh "mvn clean install -Dmaven.test.skip=true"
                }
                stage("Test") {
                    withEnv(["RPC_BIND_IP=${env.OPENSHIFT_JENKINS_IP}"]) {
                        sh "mvn test"
                        junit '**/target/*-reports/TEST-*.xml'
                    }
                }
            }
        }
        currentBuild.result = "SUCCESS"
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        def recipients = emailextrecipients([
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'FailingTestSuspectsRecipientProvider'],
                [$class: 'FirstFailingBuildSuspectsRecipientProvider'],
                [$class: 'DevelopersRecipientProvider'],
                [$class: 'RequesterRecipientProvider']
        ])

        echo recipients

        step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: recipients, sendToIndividuals: true])
    }
}
