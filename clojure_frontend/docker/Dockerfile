FROM dyne/clojure
LABEL maintainer="Denis Roio <jaromil@dyne.org>" \
	  homepage="https://github.com/dyne/agiladmin"
# ENV VERSION=AUTO_STRICT

EXPOSE 3000

WORKDIR /app
COPY . /app

RUN  mkdir -p /usr/share/man/man1/ \
	 && apt-get update \
	 && apt-get install -y -q --allow-downgrades --no-install-recommends \
	 		 mongodb \
	 && apt-get clean \
	 && lein deps

CMD /etc/init.d/mongodb start \
	&& lein ring server-headless
