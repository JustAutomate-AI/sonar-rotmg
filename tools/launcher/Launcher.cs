using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

/// Starts Sonar from the run folder with the Java that Setup selected.
static class Launcher
{
    [STAThread]
    static int Main(string[] args)
    {
        string root = AppDomain.CurrentDomain.BaseDirectory;
        string run = Path.Combine(root, "run");
        string jar = Path.Combine(run, "Sonar.jar");
        string javaFile = Path.Combine(run, "java-path.txt");
        string javaw = File.Exists(javaFile) ? File.ReadAllText(javaFile).Trim() : "";

        if (!File.Exists(jar) || !File.Exists(javaw))
        {
            string setup = Path.Combine(root, "Setup.bat");
            DialogResult answer = MessageBox.Show(
                "Sonar is not set up yet, or Java was moved.\n\nRun Setup now?",
                "Sonar", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (answer == DialogResult.Yes && File.Exists(setup))
            {
                Process.Start(new ProcessStartInfo(setup) { WorkingDirectory = root });
            }
            return 1;
        }

        string arguments = "-jar Sonar.jar";
        foreach (string a in args) arguments += " \"" + a.Replace("\"", "\\\"") + "\"";

        try
        {
            Process.Start(new ProcessStartInfo(javaw, arguments) { WorkingDirectory = run, UseShellExecute = false });
        }
        catch (Exception e)
        {
            MessageBox.Show("Could not start Sonar:\n" + e.Message + "\n\nRun Diagnose.bat to find the cause.",
                "Sonar", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
        return 0;
    }
}
