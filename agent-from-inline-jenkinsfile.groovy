pipeline {
    agent {
        kubernetes {
            cloud 'openshift'
            yaml '''
kind: Pod
spec:
  containers:
  - name: jnlp
    image: image-registry.openshift-image-registry.svc:5000/openshift/jenkins-agent-base:latest
    imagePullPolicy: Always
    workingDir: /home/jenkins/agent
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
  - name: java
    image: image-registry.openshift-image-registry.svc:5000/pipeline-environment/custom-jenkins-agent-sidecar:openjdk-8-ubi8
    imagePullPolicy: Always
    workingDir: /home/jenkins/agent
    command:
    - cat
    tty: true
'''
            defaultContainer 'java'
        }
    }
    stages {
        stage('Main') {
            steps {
                sh "java -version"
                sh "mvn --version"
                sh "jq --version"
                sh "skopeo --version"
            }
        }
    }
}
