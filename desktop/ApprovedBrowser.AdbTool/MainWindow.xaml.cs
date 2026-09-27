using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
namespace ApprovedBrowser.AdbTool;
public partial class MainWindow : Window
{
    private const string PackageName="com.zygy7678.approvedbrowser";
    private const string AdminComponent=PackageName+"/.ApprovedBrowserDeviceAdminReceiver";
    public MainWindow(){InitializeComponent();Loaded+=async(_,_)=>await RefreshStatusAsync();}
    private async void RefreshButton_Click(object sender,RoutedEventArgs e)=>await RefreshStatusAsync();
    private async Task RefreshStatusAsync(){SetBusy(true);try{var r=await RunAdbAsync("get-state");if(r.ExitCode==0&&r.Output.Trim().Equals("device",StringComparison.OrdinalIgnoreCase)){ConnectionBadge.Text="מחובר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.LightGreen;DeviceStatusText.Text="מכשיר Android מחובר ומוכן לקבלת פקודת ADB";AppendLog("✓ ADB: המכשיר מחובר");}else{ConnectionBadge.Text="לא מחובר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.Orange;DeviceStatusText.Text="חבר את המכשיר ב־USB ואשר ניפוי USB";AppendLog("! ADB: המכשיר לא זוהה");if(!string.IsNullOrWhiteSpace(r.Error))AppendLog(r.Error.Trim());}}catch(Exception ex){ConnectionBadge.Text="ADB חסר";ConnectionBadge.Foreground=System.Windows.Media.Brushes.OrangeRed;DeviceStatusText.Text="לא נמצא adb במחשב. ודא שהוא נמצא ב־PATH";AppendLog("✕ "+ex.Message);}finally{SetBusy(false);}}
    private async void SetOwnerButton_Click(object sender, RoutedEventArgs e)
    {
        var password = AccessCodeBox.Password.Trim();
        var confirmation = AccessCodeConfirmBox.Password.Trim();

        if (!System.Text.RegularExpressions.Regex.IsMatch(password, @"^\d{4,12}$"))
        {
            MessageBox.Show("קוד הגישה חייב להכיל 4–12 ספרות.", "קוד לא תקין", MessageBoxButton.OK, MessageBoxImage.Warning);
            AccessCodeBox.Focus();
            return;
        }

        if (!string.Equals(password, confirmation, StringComparison.Ordinal))
        {
            MessageBox.Show("קודי הגישה אינם זהים.", "קוד לא תקין", MessageBoxButton.OK, MessageBoxImage.Warning);
            AccessCodeConfirmBox.Focus();
            return;
        }

        SetBusy(true);
        try
        {
            AppendLog("> בודק שהאפליקציה מותקנת...");
            var installed = await RunAdbAsync("shell pm path " + PackageName);
            if (installed.ExitCode != 0 || string.IsNullOrWhiteSpace(installed.Output))
            {
                AppendLog("! האפליקציה אינה מותקנת — מתקין אותה מתוך כלי המחשב...");
                var apkPath = await ExtractBundledApkAsync();
                try
                {
                    var installCommand = "install -r -t \"" + apkPath + "\"";
                    var install = await RunAdbAsync(installCommand);
                    if (install.ExitCode != 0 || !((install.Output + install.Error).Contains("Success", StringComparison.OrdinalIgnoreCase)))
                    {
                        AppendLog("✕ התקנת האפליקציה נכשלה");
                        if (!string.IsNullOrWhiteSpace(install.Error)) AppendLog(install.Error.Trim());
                        MessageBox.Show(string.IsNullOrWhiteSpace(install.Error) ? "התקנת האפליקציה נכשלה." : install.Error.Trim(), "שגיאת התקנה", MessageBoxButton.OK, MessageBoxImage.Error);
                        return;
                    }
                    AppendLog("✓ האפליקציה הותקנה בהצלחה מתוך כלי המחשב");
                }
                finally { try { File.Delete(apkPath); } catch { } }
            }

            AppendLog("> בודק בעל מכשיר קיים...");
            var owners = await RunAdbAsync("shell dpm list-owners");
            var ownerText = (owners.Output + Environment.NewLine + owners.Error).Trim();

            if (ownerText.Contains("Device Owner", StringComparison.OrdinalIgnoreCase) &&
                !ownerText.Contains(AdminComponent, StringComparison.Ordinal))
            {
                AppendLog("✕ קיים בעל מכשיר אחר");
                MessageBox.Show("המכשיר כבר מנוהל על ידי בעל מכשיר אחר. יש להסיר את הניהול הקיים או להשתמש במכשיר שהוכן לכך לפני הפעלת דפדפן מאושר.", "בעל מכשיר קיים", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            var alreadyOwner = ownerText.Contains(AdminComponent, StringComparison.Ordinal);
            if (alreadyOwner)
            {
                AppendLog("✓ דפדפן מאושר כבר מוגדר כבעל המכשיר");
            }
            else
            {
                var confirm = MessageBox.Show(
                    "הכלי יגדיר את דפדפן מאושר כבעל המכשיר ולאחר מכן יגדיר את קוד הגישה שבחרת באפליקציה.\n\nהמשך?",
                    "אישור הגנת המכשיר",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question);

                if (confirm != MessageBoxResult.Yes)
                    return;

                AppendLog("> שולח פקודת ADB להגדרת בעל המכשיר...");
                var ownerResult = await RunAdbAsync($"shell dpm set-device-owner {AdminComponent}");
                if (ownerResult.ExitCode != 0)
                {
                    AppendLog("✕ הגדרת בעל המכשיר נכשלה");
                    if (!string.IsNullOrWhiteSpace(ownerResult.Error))
                        AppendLog(ownerResult.Error.Trim());

                    MessageBox.Show(
                        string.IsNullOrWhiteSpace(ownerResult.Error)
                            ? "פקודת בעל המכשיר נכשלה. ייתכן שהמכשיר כבר הוגדר או אינו מוכן להגדרת Device Owner."
                            : ownerResult.Error.Trim(),
                        "שגיאת ADB",
                        MessageBoxButton.OK,
                        MessageBoxImage.Error);
                    return;
                }

                AppendLog("✓ בעל המכשיר הוגדר בהצלחה");
            }

            var encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes(password));
            AppendLog("> מגדיר את קוד הגישה באפליקציה...");
            var setupResult = await RunAdbAsync(
                $"shell am start -W -n {PackageName}/.MainActivity --es setup_access_code_b64 {encoded}");

            if (setupResult.ExitCode != 0)
            {
                AppendLog("✕ הגדרת קוד הגישה נכשלה");
                if (!string.IsNullOrWhiteSpace(setupResult.Error))
                    AppendLog(setupResult.Error.Trim());

                MessageBox.Show(
                    string.IsNullOrWhiteSpace(setupResult.Error)
                        ? "בעל המכשיר הוגדר, אבל קוד הגישה לא הועבר לאפליקציה."
                        : setupResult.Error.Trim(),
                    "שגיאה בהגדרת הקוד",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
                return;
            }

            AppendLog("✓ קוד הגישה הוגדר באפליקציה");
            AppendLog("✓ ההגדרה הושלמה");
            MessageBox.Show(
                "ההגדרה הושלמה בהצלחה.\n\nהאפליקציה מוגדרת כבעלת המכשיר וקוד הגישה שבחרת פעיל.",
                "הכול מוכן",
                MessageBoxButton.OK,
                MessageBoxImage.Information);

            AccessCodeBox.Clear();
            AccessCodeConfirmBox.Clear();
            await RefreshStatusAsync();
        }
        catch (Exception ex)
        {
            AppendLog("✕ " + ex.Message);
            MessageBox.Show(ex.Message, "שגיאה", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private static async Task<string> ExtractBundledApkAsync()
    {
        await using var input = Assembly.GetExecutingAssembly().GetManifestResourceStream("ApprovedBrowser.Apk")
            ?? throw new InvalidOperationException("קובץ ה־APK לא נכלל בכלי המחשב.");
        var path = Path.Combine(Path.GetTempPath(), "ApprovedBrowser-" + Guid.NewGuid().ToString("N") + ".apk");
        await using var output = File.Create(path);
        await input.CopyToAsync(output);
        return path;
    }

    private static async Task<AdbResult> RunAdbAsync(string arguments){var psi=new ProcessStartInfo{FileName="adb",Arguments=arguments,UseShellExecute=false,RedirectStandardOutput=true,RedirectStandardError=true,CreateNoWindow=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8};using var p=new Process{StartInfo=psi};p.Start();var o=p.StandardOutput.ReadToEndAsync();var e=p.StandardError.ReadToEndAsync();await p.WaitForExitAsync();return new AdbResult(p.ExitCode,await o,await e);}
    private void SetBusy(bool busy){SetOwnerButton.IsEnabled=!busy;System.Windows.Input.Mouse.OverrideCursor=busy?System.Windows.Input.Cursors.Wait:null;}
    private void AppendLog(string t){LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {t}{Environment.NewLine}");LogBox.ScrollToEnd();}
    private sealed record AdbResult(int ExitCode,string Output,string Error);
}