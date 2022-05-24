pipeline {
    agent {
        kubernetes {
            cloud 'openshift'
            yamlFile 'jenkins-agent-pod.yaml'
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
