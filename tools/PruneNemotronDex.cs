using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Text.RegularExpressions;

// Conservative component separation: retain entire Samsung donor namespaces,
// reflection names, manifest roots, resource classes, and their dependencies.
public static class PruneNemotronDex {
    public static string Run(string root, string component, string manifestDump) {
        root = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
        var map = new Dictionary<string,string>(StringComparer.Ordinal);
        var roots = new HashSet<string>(StringComparer.Ordinal);
        var classRegex = new Regex(@"^\.class\s+.*?(L[^;\s]+;)");
        var typeRegex = new Regex("L[^;\\s\"()<>,\\\\]+;");
        foreach (var dir in Directory.GetDirectories(root, "smali*")) {
            var dex = Path.GetFileName(dir);
            int index = dex == "smali" ? 1 : int.Parse(dex.Substring(13));
            foreach (var path in Directory.EnumerateFiles(dir, "*.smali", SearchOption.AllDirectories)) {
                var first = File.ReadLines(path).FirstOrDefault() ?? "";
                var match = classRegex.Match(first);
                if (!match.Success) throw new Exception("Missing class descriptor: " + path);
                var name = match.Groups[1].Value;
                map.Add(name,path);
                bool ownResources = name.StartsWith("Lcom/nemotron/voiceime/R;") || name.StartsWith("Lcom/nemotron/voiceime/R$");
                bool keep = ownResources || name.StartsWith("Lkotlin/") || name.StartsWith("Lkotlinx/") ||
                    name.StartsWith("Landroid/support/") || name.EndsWith("Parcelizer;", StringComparison.Ordinal);
                if (component == "ui") {
                    keep |= index >= 18 || name.StartsWith("Lcom/nemotron/oneui/") ||
                        name.StartsWith("Lcom/sec/android/app/launcher/") ||
                        name.StartsWith("Lcom/nemotron/voiceime/ui/Shortcut") ||
                        name.StartsWith("Landroidx/") || name.StartsWith("Lcom/google/android/material/");
                } else if (component == "keyboard") {
                    keep |= index >= 14 || name.StartsWith("Lcom/samsung/android/honeyboard/") ||
                        name.StartsWith("Lcom/samsung/android/writingtoolkit/") ||
                        name.StartsWith("Lcom/samsung/android/fontchooser/") ||
                        name.StartsWith("Lcom/nemotron/honeyboard/") ||
                        name.StartsWith("Lcom/nemotron/voiceime/record/") ||
                        name.StartsWith("Lcom/nemotron/voiceime/whisper/");
                } else throw new Exception("Unsupported component");
                if (keep) roots.Add(name);
            }
        }
        foreach (Match m in Regex.Matches(manifestDump, "\"([A-Za-z_$][A-Za-z0-9_.$]+)\"")) {
            var candidate = "L" + m.Groups[1].Value.Replace('.', '/') + ";";
            if (map.ContainsKey(candidate)) roots.Add(candidate);
        }
        var keepSet = new HashSet<string>(roots, StringComparer.Ordinal);
        var queue = new Queue<string>(roots);
        while (queue.Count > 0) {
            var text = File.ReadAllText(map[queue.Dequeue()]);
            foreach (Match m in typeRegex.Matches(text)) {
                if (map.ContainsKey(m.Value) && keepSet.Add(m.Value)) queue.Enqueue(m.Value);
            }
            foreach (Match m in Regex.Matches(text, "const-string(?:/jumbo)?[^\r\n]*?\"([A-Za-z_$][A-Za-z0-9_.$]+)\"")) {
                var candidate = "L" + m.Groups[1].Value.Replace('.', '/') + ";";
                if (map.ContainsKey(candidate) && keepSet.Add(candidate)) queue.Enqueue(candidate);
            }
        }
        var removed = map.Keys.Where(n => !keepSet.Contains(n)).OrderBy(n => n).ToArray();
        File.WriteAllLines(Path.Combine(root,"pruned-classes.txt"),removed);
        foreach (var name in removed) {
            var path = Path.GetFullPath(map[name]);
            if (!path.StartsWith(root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase) ||
                Path.GetExtension(path) != ".smali") throw new Exception("Unsafe generated path");
            File.Delete(path);
        }
        return component + ": classes before=" + map.Count + ", retained=" + keepSet.Count + ", removed=" + removed.Length;
    }
}
