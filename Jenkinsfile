
def emailNotification() {
    def to = emailextrecipients([[$class: 'CulpritsRecipientProvider'],
                                 [$class: 'DevelopersRecipientProvider'],
                                 [$class: 'RequesterRecipientProvider']])
    String currentResult = currentBuild.result
    String previousResult = currentBuild.getPreviousBuild().result

    def causes = currentBuild.rawBuild.getCauses()
    // E.g. 'started by user', 'triggered by scm change'
    def cause = null
    if (!causes.isEmpty()) {
        cause = causes[0].getShortDescription()
    }

    // Ensure we don't keep a list of causes, or we get
    // "java.io.NotSerializableException: hudson.model.Cause$UserIdCause"
    // see http://stackoverflow.com/a/37897833/509706
    causes = null

    String subject = "$env.JOB_NAME $env.BUILD_NUMBER: $currentResult"

    String body = """
<p>Build $env.BUILD_NUMBER ran on $env.NODE_NAME and terminated with $currentResult.
</p>

<p>Build trigger: $cause</p>

<p>See: <a href="$env.BUILD_URL">$env.BUILD_URL</a></p>

"""

    String log = currentBuild.rawBuild.getLog(40).join('\n')
    if (currentBuild != 'SUCCESS') {
        body = body + """
<h2>Last lines of output</h2>
<pre>$log</pre>
"""
    }

    if (to != null && !to.isEmpty()) {
        // Email on any failures, and on first success.
        if (currentResult != 'SUCCESS' || currentResult != previousResult) {
            emailext(
                    body: body,
                    mimeType: 'text/html',
                    replyTo: '$DEFAULT_REPLYTO',
                    subject: subject,
                    to: to,
                    attachLog: currentResult != 'SUCCESS',
                    recipientProviders: [
                            [$class: 'CulpritsRecipientProvider'],
                            [$class: 'RequesterRecipientProvider']
                    ]
            )
        }
        echo 'Sent email notification'
    }
}

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
        currentBuild.result = "SUCCESS";
    } catch(e) {
        currentBuild.result = "FAILURE";
        throw e;
    } finally {
        emailNotification()
    }
}
