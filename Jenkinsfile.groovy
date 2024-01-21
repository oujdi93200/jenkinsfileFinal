def project_token = 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEF'

properties([
    gitLabConnection('your-gitlab-connection-name'),
    pipelineTriggers([
        [
            $class: 'GitLabPushTrigger',
            branchFilterType: 'All',
            triggerOnPush: true,
            triggerOnMergeRequest: true,
            triggerOpenMergeRequestOnPush: "never",
            triggerOnNoteRequest: true,
            noteRegex: "Jenkins please retry a build",
            skipWorkInProgressMergeRequest: true,
            secretToken: project_token,
            ciSkip: false,
            setBuildDescription: true,
            addNoteOnMergeRequest: true,
            addCiMessage: true,
            addVoteOnMergeRequest: true,
            acceptMergeRequestOnSuccess: true,
            branchFilterType: "NameBasedFilter",
            includeBranchesSpec: "",
            excludeBranchesSpec: ""
        ]
    ])
])

node {
    try {
        def buildNum = env.BUILD_NUMBER
        def branchName = env.BRANCH_NAME

        print buildNum
        print branchName

        stage('Env - clone generator') {
            git "http://gitlab.esgi.lab/dev/postgres.git"
        }

        stage('Env - run postgres') {
            sh "./generator.sh -p"
            sh "docker ps -a"
        }

        stage('SERVICE - Git checkout') {
            git branch: branchName, url: "http://gitlab.esgi.lab/dev/version.git"
        }

        def extension = ''
        if (branchName == "dev") {
            extension = "test"
        } else if (branchName == "master") {
            extension = "qual"
        } else if (branchName == "prod") {
            extension = "rc"
        }

        def commitIdLong = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        def commitId = commitIdLong.take(7)
        sh "sed -i s/-XXX/${extension}/g pom.xml"
        def version = sh(script: "perl -nle 'm{.*<version>(.*)</version>.*};print \$1' pom.xml", returnStdout: true).trim()

        print """
        #################################################
           BranchName: $branchName
           CommitID: $commitId
           AppVersion: $version
           JobNumber: $buildNum
        #################################################
        """
    } catch (Exception e) {
        throw e
    } finally {
        cleanWs()
    }
}

