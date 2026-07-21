using OEIMS.Sentinel.Agent.Domain;

namespace OEIMS.Sentinel.Agent.Platform.Windows;

internal sealed class ExamIdentityCodeOverlay : IExamIdentityCodeOverlay, IDisposable
{
    private static readonly TimeSpan StartupTimeout = TimeSpan.FromSeconds(5);

    private readonly ManualResetEventSlim _ready = new();
    private readonly Thread _uiThread;

    private SynchronizationContext? _uiContext;
    private ExamIdentityCodeOverlayForm? _form;
    private bool _disposed;

    public ExamIdentityCodeOverlay()
    {
        _uiThread = new Thread(RunMessageLoop)
        {
            IsBackground = true,
            Name = "OEIMS Agent Overlay"
        };

        _uiThread.SetApartmentState(ApartmentState.STA);
        _uiThread.Start();

        if (!_ready.Wait(StartupTimeout))
            throw new InvalidOperationException("Agent overlay UI thread did not start.");
    }

    public Task ShowAsync(string code, CancellationToken ct = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(code);

        if (_disposed)
            throw new ObjectDisposedException(nameof(ExamIdentityCodeOverlay));

        ct.ThrowIfCancellationRequested();

        var uiContext = _uiContext
            ?? throw new InvalidOperationException("Agent overlay UI thread is not available.");

        var completion = new TaskCompletionSource<object?>(
            TaskCreationOptions.RunContinuationsAsynchronously);

        uiContext.Post(_ =>
        {
            try
            {
                if (_form is null || _form.IsDisposed)
                {
                    _form = new ExamIdentityCodeOverlayForm(code);
                    _form.FormClosed += (_, _) => _form = null;
                    _form.Show();
                }
                else
                {
                    _form.SetCode(code);
                    _form.Show();
                }

                _form.Activate();

                completion.SetResult(null);
            }
            catch (Exception ex)
            {
                completion.SetException(ex);
            }
        }, null);

        return completion.Task.WaitAsync(ct);
    }

    private void RunMessageLoop()
    {
        SynchronizationContext.SetSynchronizationContext(
            new WindowsFormsSynchronizationContext());

        _uiContext = SynchronizationContext.Current;
        _ready.Set();

        Application.Run();
    }

    public void Dispose()
    {
        if (_disposed)
            return;

        _disposed = true;

        _uiContext?.Post(_ => Application.ExitThread(), null);
        _uiThread.Join(TimeSpan.FromSeconds(2));

        _ready.Dispose();
    }
}


internal sealed class ExamIdentityCodeOverlayForm : Form
{
    internal const string WindowTitle = "OEIMS Exam Code Overlay";
    internal const string WindowClassName = "OEIMS.ExamIdentityCodeOverlay";

    private readonly Label _codeLabel;

    public ExamIdentityCodeOverlayForm(string code)
    {
        Text = WindowTitle;
        AccessibleName = WindowTitle;

        TopMost = true;
        ShowInTaskbar = true;
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(520, 260);

        var title = new Label
        {
            Text = "Exam entry code",
            Dock = DockStyle.Fill,
            AutoSize = true,
            Font = new Font(Font.FontFamily, 14, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleCenter
        };

        _codeLabel = new Label
        {
            Text = code,
            Dock = DockStyle.Fill,
            AutoSize = false,
            Font = new Font(FontFamily.GenericMonospace, 32, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleCenter,
            AccessibleName = "Exam identity code"
        };

        var description = new Label
        {
            Text = "Type this code in the first page of the exam form.",
            Dock = DockStyle.Fill,
            AutoSize = true,
            TextAlign = ContentAlignment.MiddleCenter
        };

        var closeButton = new Button
        {
            Text = "I copied the code",
            Dock = DockStyle.Fill,
            AutoSize = true
        };

        closeButton.Click += (_, _) => Close();

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(24),
            RowCount = 4,
            ColumnCount = 1
        };

        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        layout.Controls.Add(title, 0, 0);
        layout.Controls.Add(_codeLabel, 0, 1);
        layout.Controls.Add(description, 0, 2);
        layout.Controls.Add(closeButton, 0, 3);

        Controls.Add(layout);
    }

    public void SetCode(string code)
    {
        _codeLabel.Text = code;
    }

    protected override CreateParams CreateParams
    {
        get
        {
            var parameters = base.CreateParams;
            parameters.ClassName = WindowClassName;
            return parameters;
        }
    }
}