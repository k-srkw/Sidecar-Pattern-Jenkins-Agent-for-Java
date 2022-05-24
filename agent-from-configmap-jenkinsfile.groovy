pipeline {
    agent {
        label 'custom-java-builder'
    }
    stages {
        stage('Main') {
            steps {
                container("java") {
                    sh "java -version"
                    sh "mvn --version"
                    sh "jq --version"
                    sh "skopeo --version"
                }
            }
        }
    }
}
