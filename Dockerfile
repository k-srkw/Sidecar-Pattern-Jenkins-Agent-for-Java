FROM image-registry.openshift-image-registry.svc:5000/openshift/java:openjdk-8-ubi8
USER root
RUN  microdnf install --setopt=install_weak_deps=0 --setopt=tsflags=nodocs -y jq skopeo \
     && microdnf clean all && [ ! -d /var/cache/yum ] || rm -rf /var/cache/yum \
     && rpm -q jq skopeo
USER 185
