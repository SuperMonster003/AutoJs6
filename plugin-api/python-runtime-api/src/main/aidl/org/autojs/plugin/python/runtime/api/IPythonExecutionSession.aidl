package org.autojs.plugin.python.runtime.api;

oneway interface IPythonExecutionSession {

    void start();

    void grantOutputCredits(int count);

    void cancel();

    void close();

    void replyInput(in byte[] reply);
}
