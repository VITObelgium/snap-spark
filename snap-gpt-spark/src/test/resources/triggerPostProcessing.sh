DIRNAME=$(dirname "$0")
docker run --env PYTHONUNBUFFERED=1 -v ${DIRNAME}/:${DIRNAME}/ -v /tmp/:/tmp/ vito-docker-private.artifactory.vgt.vito.be/python36 /bin/sh -c "python36 ${DIRNAME}/postprocess.py $1"
