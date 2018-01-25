pipeline {
  agent any
  stages {
    stage('compile') {
      steps {
        build 'mvn clean package -DskipTests'
      }
    }
  }
}