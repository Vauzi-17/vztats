package com.vauzi.vztats.shizuku;

interface IUserService {
    // Reserved id used by Shizuku to tear the user service down.
    void destroy() = 16777114;

    // Runs a shell command in the shell-privileged user-service process and
    // returns its stdout.
    String exec(String command) = 1;
}
