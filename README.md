# Sidecar Pattern Jenkins Agent for Java

## Running Jenkins service

### Enabling monitoring for user-defined projects (Optional)

```
oc apply -f cluster-monitoring-config.yaml
```

### Creating project

```
oc new-project pipeline-environment
```

### Creating a Jenkins service from a template

```
oc process openshift//jenkins-persistent-monitored -p VOLUME_CAPACITY=2Gi | oc -n pipeline-environment apply -f -
```

### Accessing a Jenkins service

```
echo https://$(oc -n pipeline-environment get route jenkins -ojsonpath='{.spec.host}')/
```

## Adding customized Jenkins Agent from ConfigMap

### Building customized Jenkins Agent sidecar image

```
oc -n pipeline-environment apply -f custom-jenkins-agent-sidecar-build.yaml
oc -n pipeline-environment start-build custom-jenkins-agent-sidecar
```

### Creating a Pod Template

```
oc -n pipeline-environment apply -f jenkins-agents-configmap.yaml
```

### To debug image (or base image) for Jenkins Agent sidecar container

- Base Image

    ```
    oc run java --image=image-registry.openshift-image-registry.svc:5000/openshift/java:openjdk-8-ubi8 -it --rm --overrides='{"spec":{"securityContext":{"runAsUser":0}}}' --command -- /bin/bash
    ```

- Custom Jenkins Agent Sidecar Image

    ```
    oc run java --image=image-registry.openshift-image-registry.svc:5000/pipeline-environment/custom-jenkins-agent-sidecar:openjdk-8-ubi8 -it --rm --overrides='{"spec":{"securityContext":{"runAsUser":0}}}' --command -- /bin/bash
    ```

## Creating Jenkins Job
