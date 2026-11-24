package com.example;

import android.os.ParcelFileDescriptor;

interface IShellService {
    void destroy() = 16777114;
    String exec(String command) = 1;
    int getUid() = 2;
    String installApks(in ParcelFileDescriptor[] fds, in long[] sizes, in String[] names) = 3;
}
