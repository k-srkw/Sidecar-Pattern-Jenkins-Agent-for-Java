# Sidecar Pattern Jenkins Agent for Java

## Running Jenkins service

### Enabling monitoring for user-defined projects (Optional)

Jenkins Server のメトリクスを Prometheus で監視する場合、 [Monitoring for user-defined projects](https://docs.openshift.com/container-platform/4.10/monitoring/enabling-monitoring-for-user-defined-projects.html) を有効化します。

```
oc apply -f cluster-monitoring-config.yaml
```

### Creating project

```
oc new-project pipeline-environment
```

### Creating a Jenkins service from a template

Jenkins Server のメトリクスを Prometheus で監視する場合は `jenkins-persistent-monitored` または `jenkins-ephemeral-monitored` を利用します。  
監視が不要な場合は `jenkins-persistent` または `jenkins-ephemeral` を利用します。

デフォルトで Cluster Samples Operator が管理する Jenkins Image Stream を利用しますが、必要に応じて最新の Jenkins Server イメージを利用してください。

```
oc import-image ocp-tools-4/jenkins-rhel8:v4.10.0 --from=registry.redhat.io/ocp-tools-4/jenkins-rhel8:v4.10.0 --confirm
```

- Jenkins Plugin の追加や更新を行わない場合

    以下は Cluster Samples Operator が管理する Jenkins Image Stream (`jenkins:2`) を利用する例です。パラメータは必要に応じて変更してください。

    ```
    oc process openshift//jenkins-persistent-monitored \
      -p VOLUME_CAPACITY=2Gi \
      | oc -n pipeline-environment apply -f -
    ```

    以下は import した最新の Jenkins イメージを利用する例です。

    ```
    oc process openshift//jenkins-persistent-monitored \
      -p VOLUME_CAPACITY=2Gi \
      -p NAMESPACE=pipeline-environment \
      -p JENKINS_IMAGE_STREAM_TAG=ocp-tools-4/jenkins-rhel8:v4.10.0 \
      | oc -n pipeline-environment apply -f -
    ```

- Jenkins Plugin を追加、更新する場合

    S2I ビルドにより必要なプラグインを追加したカスタム　Jenkins イメージを作成します。  
    この Git リポジトリ上からプラグインを取得します。このリポジトリでは例として [Pipeline Stage View Plugin](https://plugins.jenkins.io/pipeline-stage-view/#documentation) をインストールします。
    
    - `plugins.txt` : Jenkins Update Site からダウンロード、インストールするプラグインのリスト。インターネットアクセス可能な場合はこちらにプラグインを定義する。
    - `plugins` ディレクトリ : ネットワーク的に隔離された環境の場合にインストールするプラグインファイルを格納する。 [Plugins Index](https://plugins.jenkins.io/) から事前に取得する。

    ```
    oc apply -f custom-jenkins/custom-jenkins-build.yaml
    oc start-build custom-jenkins
    ```

    作成したカスタム　Jenkins イメージで Jenkins Server を起動します。

    ```
    oc process openshift//jenkins-persistent-monitored \
      -p VOLUME_CAPACITY=2Gi \
      -p NAMESPACE=pipeline-environment \
      -p JENKINS_IMAGE_STREAM_TAG=custom-jenkins:latest \
      | oc -n pipeline-environment apply -f -
    ```

### Resolve Dependency of Jenkins Plugin

ネットワーク的に隔離された環境の場合、インストールしたいプラグインおよびその依存プラグインも含め Git リポジトリ上にプラグインファイルを格納しておく必要があります。

以下の方法である程度格納が必要な依存プラグインを確認することができます。

1. [Plugins Index](https://plugins.jenkins.io/) でインストールしたいプラグインを検索
2. プラグインのページの `Dependencies` タブで `Required` となっているプラグインを確認
3. Jenkins Server イメージのリポジトリ ([openshift/jenkins](https://github.com/openshift/jenkins)) で `release-<version>` ブランチに切り替え、`/2/contrib/openshift/` 配下の `base-plugins.txt` および `bundle-plugins.txt`　を確認し、依存プラグインの該当バージョンがインストール済みであれば取得不要
4. インストール済みでない場合 `plugins.txt` に追加し、 `plugins` ディレクトリにファイルを格納する
    - この時プラグインのリポジトリ上で `pom.xml` の `<jenkins.version>` を確認し、 Jenkins Server イメージがバージョンの要件を満たしているか確認する。満たさない場合プラグインのリポジトリ上でリリースタグを遡り、要件を満たすバージョンを確認する。
    - Jenkins Server イメージのバージョンは Jenkins Server イメージのリポジトリの `/2/contrib/openshift/` 配下の `jenkins-version.txt` で確認できる。
5. 追加した依存プラグインの依存プラグインに対して 2-4 を繰り返す

### Accessing a Jenkins service

Jenkins Server の URL を確認し、ブラウザからアクセスできることを確認します。

```
echo https://$(oc -n pipeline-environment get route jenkins -ojsonpath='{.spec.host}')/
```

## Building customized Jenkins Agent sidecar image

OpenShift 4.10 以降で推奨される [Sidecar パターンを採用した Jenkins Agent Pod Template](https://docs.openshift.com/container-platform/4.10/openshift_images/using_images/images-other-jenkins.html#images-other-jenkins-config-kubernetes_images-other-jenkins) を利用するために、カスタムの Sidecar コンテナイメージを作成します。  
このサンプルでのカスタム Jenkins Agent Pod の構成は以下となります。

- jnlp : JNLP プロトコルで Jenkins Server と通信するコンテナ 
- java : OpenJDK、Maven に加え jq、skopeo を追加した Sidecar コンテナ。実際のパイプラインの処理を担当する。

このサンプルではカスタムの Sidecar コンテナイメージを BuildConfig により OpenShift 上でビルドします。  
BuildConfig および ImageStream は [custom-jenkins-agent-sidecar-build.yaml](custom-jenkins-agent-sidecar-build.yaml) に定義しています。  
BuildConfig では Docker Strategy により [Dockerfile](Dockerfile) でビルドを行います。

```
oc -n pipeline-environment apply -f custom-agent/custom-jenkins-agent-sidecar-build.yaml
oc -n pipeline-environment start-build custom-jenkins-agent-sidecar
```

サンプルのカスタム Sidecar コンテナのベースイメージは [Universal Base Images (UBI)](https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/8/html/building_running_and_managing_containers/assembly_types-of-container-images_building-running-and-managing-containers#con_characteristics-of-ubi-images_assembly_types-of-container-images) をベースとした [ubi8/openjdk-8](https://catalog.redhat.com/software/containers/ubi8/openjdk-8/5dd6a48dbed8bd164a09589a) イメージを利用します。  
RHEL サブスクリプションの登録なしに RHEL パッケージのサブセットである `ubi-8-baseos` および `ubi-8-appstream` リポジトリが利用できるため、 UBI ベースのベースイメージを採用しています。

`ubi8/openjdk-8` イメージは Cluster Samples Operator の設定で `managementState` が `Managed` となっている場合 OpenShift の内部レジストリに格納され、 `java` ImageStream で管理されるためこれを利用します。  
格納先および ImageStreamTag の情報は `java` ImageStream から以下のように確認できます。

```
$ oc -n openshift get is java -oyaml
<省略>
status:
  dockerImageRepository: image-registry.openshift-image-registry.svc:5000/openshift/java
  publicDockerImageRepository: default-route-openshift-image-registry.apps.cluster-b5dqk.b5dqk.sandbox1734.opentlc.com/openshift/java
  tags:
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/openjdk/openjdk-11-rhel7@sha256:a55ede0c4b60edc130e7ecc54d34b695a8807c8b23dc85a3ef100964da1f65aa
      generation: 2
      image: sha256:a55ede0c4b60edc130e7ecc54d34b695a8807c8b23dc85a3ef100964da1f65aa
    tag: "11"
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/redhat-openjdk-18/openjdk18-openshift@sha256:a98257afea2078d3423168c5d5c51655c748ba29bf11ea1b4799237a65cf8a66
      generation: 2
      image: sha256:a98257afea2078d3423168c5d5c51655c748ba29bf11ea1b4799237a65cf8a66
    tag: "8"
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/ubi8/openjdk-17@sha256:578c01aa5439b0f054490f51f384009120eee5ce0f8ab457764e731243e23863
      generation: 2
      image: sha256:578c01aa5439b0f054490f51f384009120eee5ce0f8ab457764e731243e23863
    tag: latest
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/openjdk/openjdk-11-rhel7@sha256:a55ede0c4b60edc130e7ecc54d34b695a8807c8b23dc85a3ef100964da1f65aa
      generation: 2
      image: sha256:a55ede0c4b60edc130e7ecc54d34b695a8807c8b23dc85a3ef100964da1f65aa
    tag: openjdk-11-el7
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/ubi8/openjdk-11@sha256:292ddb499cc3eb759482e31d4b2f948f5312e4316d21d11d2d8a7b2e6ef13315
      generation: 2
      image: sha256:292ddb499cc3eb759482e31d4b2f948f5312e4316d21d11d2d8a7b2e6ef13315
    tag: openjdk-11-ubi8
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/ubi8/openjdk-17@sha256:578c01aa5439b0f054490f51f384009120eee5ce0f8ab457764e731243e23863
      generation: 2
      image: sha256:578c01aa5439b0f054490f51f384009120eee5ce0f8ab457764e731243e23863
    tag: openjdk-17-ubi8
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/redhat-openjdk-18/openjdk18-openshift@sha256:a98257afea2078d3423168c5d5c51655c748ba29bf11ea1b4799237a65cf8a66
      generation: 2
      image: sha256:a98257afea2078d3423168c5d5c51655c748ba29bf11ea1b4799237a65cf8a66
    tag: openjdk-8-el7
  - items:
    - created: "2022-05-24T04:20:58Z"
      dockerImageReference: registry.redhat.io/ubi8/openjdk-8@sha256:a6a912a16608a3265bee6d66d3610a965ffbd33702aa4bbb181a7fab17e2438f
      generation: 2
      image: sha256:a6a912a16608a3265bee6d66d3610a965ffbd33702aa4bbb181a7fab17e2438f
    tag: openjdk-8-ubi8
```

### To debug image (or base image) for Jenkins Agent sidecar container

カスタムの Sidecar コンテナイメージビルド時に Dockerfile の定義を検証したい場合、ベースイメージから root ユーザでコンテナを起動し検証を行います。  
すでに作成したカスタムの Sidecar コンテナイメージの動作確認をしたい場合は Sidecar コンテナを起動し動作確認を行います。

- Base Image

    ```
    oc run java --image=image-registry.openshift-image-registry.svc:5000/openshift/java:openjdk-8-ubi8 -it --rm --overrides='{"spec":{"securityContext":{"runAsUser":0}}}' --command -- /bin/bash
    ```

- Custom Jenkins Agent Sidecar Image

    ```
    oc run java --image=image-registry.openshift-image-registry.svc:5000/pipeline-environment/custom-jenkins-agent-sidecar:openjdk-8-ubi8 -it --rm --overrides='{"spec":{"securityContext":{"runAsUser":0}}}' --command -- /bin/bash
    ```

## Adding customized Jenkins Agent to pipeline

Jenkinsfile に定義したパイプラインでカスタムの Jenkins Agent を使用する方法は以下のいずれかとなります。

- Pod Template (XML) を含む ConfigMap を作成
- Jenkinsfile に Inline で Pod Manifest を定義 (YAML)
- ファイルに Pod Manifest を定義 (YAML) し、Jenkinsfile で参照する

### Adding customized Jenkins Agent from ConfigMap

#### Creating a Pod Template

[jenkins-agents-configmap.yaml](jenkins-agents-configmap.yaml) に定義した Pod Template を含む ConfigMap を作成します。  
ラベル `role: jenkins-agent` を指定することで OpenShift Sync Plugin により ConfigMap 内の Pod Template が Jenkins Server に同期されます。

```
oc -n pipeline-environment apply -f jenkins-agents-configmap.yaml
```

#### Creating Jenkins Job

ConfigMap に定義した Pod Template を利用する Jenkinsfile は [agent-from-configmap-jenkinsfile.groovy](agent-from-configmap-jenkinsfile.groovy) に定義しています。  
Pod Template の `<label>custom-java-builder</label>` で定義した Agent のラベルを以下のように指定しています。

```groovy
    agent {
        label 'custom-java-builder'
    }
```

また、 Pod にコンテナが複数含まれるため以下のように `step` の処理をどのコンテナが実行するか指定する必要があります。

```groovy
    stages {
        stage('Main') {
            steps {
                container("java") {
                    ...
                }
            }
        }
    }
```

Jenkins Server 上でのパイプラインの作成は以下の手順で実施できます。

1. Jenkins UI 上で「新規ジョブ作成」を選択し、「Enter an item name」にパイプライン名を入力します。ジョブの種別で「パイプライン」を選択し、「OK」を押します。
2. 作成されたパイプラインの設定画面にて「パイプライン」→「定義」でプルダウンリストから「Pipeline script from SCM」を選択します。選択後表示される各項目に以下のように設定を行い、「保存」を押します。
    - SCM : Git
      - リポジトリ
        - リポジトリURL : `https://github.com/k-srkw/Sidecar-Pattern-Jenkins-Agent-for-Java.git`
        - ビルドするブランチ > ブランチ指定子 : `*/main`
    - Script Path : `agent-from-configmap-jenkinsfile.groovy`

### Adding customized Jenkins Agent from Inline

#### Creating a Pod Template

Jenkinsfile 内に以下のように Inline で Pod Template を定義します。Yaml フォーマットで Pod Manifest を定義します。  
`defaultContainer` を設定することで各 `step` 実行時に実行対象のコンテナを指定しなかった場合に利用するコンテナを指定できます。

```groovy
    agent {
        kubernetes {
            cloud 'openshift'
            yaml '''
apiVersion: v1
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
```

#### Creating Jenkins Job

Inline で定義した Pod Template を利用する Jenkinsfile は [agent-from-inline-jenkinsfile.groovy](agent-from-inline-jenkinsfile.groovy) に定義しています。  
Jenkins Server 上でのパイプラインの作成は以下の手順で実施できます。

1. Jenkins UI 上で「新規ジョブ作成」を選択し、「Enter an item name」にパイプライン名を入力します。ジョブの種別で「パイプライン」を選択し、「OK」を押します。
2. 作成されたパイプラインの設定画面にて「パイプライン」→「定義」でプルダウンリストから「Pipeline script from SCM」を選択します。選択後表示される各項目に以下のように設定を行い、「保存」を押します。
    - SCM : Git
      - リポジトリ
        - リポジトリURL : `https://github.com/k-srkw/Sidecar-Pattern-Jenkins-Agent-for-Java.git`
        - ビルドするブランチ > ブランチ指定子 : `*/main`
    - Script Path : `agent-from-inline-jenkinsfile.groovy`

### Adding customized Jenkins Agent from File

#### Creating a Pod Template

Jenkinsfile 内に以下のように Pod Template 参照を定義します。参照先ファイルの [jenkins-agent-pod.yaml](jenkins-agent-pod.yaml) では Yaml フォーマットで Pod Manifest を定義します。  
`defaultContainer` を設定することで各 `step` 実行時に実行対象のコンテナを指定しなかった場合に利用するコンテナを指定できます。

```groovy
    agent {
        kubernetes {
            cloud 'openshift'
            yamlFile 'jenkins-agent-pod.yaml'
            defaultContainer 'java'
        }
    }
```

#### Creating Jenkins Job

別ファイルに定義した Pod Template を利用する Jenkinsfile は [agent-from-yamlfile-jenkinsfile.groovy](agent-from-yamlfile-jenkinsfile.groovy) に定義しています。  
Jenkins Server 上でのパイプラインの作成は以下の手順で実施できます。

1. Jenkins UI 上で「新規ジョブ作成」を選択し、「Enter an item name」にパイプライン名を入力します。ジョブの種別で「パイプライン」を選択し、「OK」を押します。
2. 作成されたパイプラインの設定画面にて「パイプライン」→「定義」でプルダウンリストから「Pipeline script from SCM」を選択します。選択後表示される各項目に以下のように設定を行い、「保存」を押します。
    - SCM : Git
      - リポジトリ
        - リポジトリURL : `https://github.com/k-srkw/Sidecar-Pattern-Jenkins-Agent-for-Java.git`
        - ビルドするブランチ > ブランチ指定子 : `*/main`
    - Script Path : `agent-from-yamlfile-jenkinsfile.groovy`
