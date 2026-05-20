using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace Lockroot.Windows.Services;

public static class WindowCaptureProtection
{
    private const uint WdaMonitor = 0x00000001;
    private const uint WdaExcludeFromCapture = 0x00000011;

    public static void Apply(Window window)
    {
        var handle = new WindowInteropHelper(window).Handle;
        if (handle == IntPtr.Zero)
        {
            return;
        }

        if (!SetWindowDisplayAffinity(handle, WdaExcludeFromCapture))
        {
            _ = SetWindowDisplayAffinity(handle, WdaMonitor);
        }
    }

    [DllImport("user32.dll")]
    private static extern bool SetWindowDisplayAffinity(IntPtr hWnd, uint dwAffinity);
}
