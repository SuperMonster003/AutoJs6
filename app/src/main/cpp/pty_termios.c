#include <jni.h>
#include <errno.h>
#include <termios.h>

/*
 * Makes the terminal behind fd behave like a pipe for text: no echo of input, no carriage return added before a line
 * feed. On a pseudo-terminal master Linux applies this to its slave, so it can be set before anything is written.
 * Returns 0 or an errno value.
 */
JNIEXPORT jint JNICALL
Java_org_autojs_autojs_runtime_api_shell_PtyTermios_setPipeLike(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    struct termios t;
    if (tcgetattr(fd, &t) != 0) return errno;
    t.c_lflag &= ~(tcflag_t) (ECHO | ECHOE | ECHOK | ECHONL);
    t.c_oflag &= ~(tcflag_t) ONLCR;
    if (tcsetattr(fd, TCSANOW, &t) != 0) return errno;
    return 0;
}
