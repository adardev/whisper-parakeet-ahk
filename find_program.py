import sys
import os
import subprocess

START_MENU = os.path.expanduser(r"~\AppData\Roaming\Microsoft\Windows\Start Menu\Programs")

def find_program(name):
    name_lower = name.lower().strip()
    results = []
    
    for root, dirs, files in os.walk(START_MENU):
        for f in files:
            if f.endswith('.lnk'):
                base = os.path.splitext(f)[0]
                if name_lower in base.lower():
                    shortcut = os.path.join(root, f)
                    try:
                        ps = f'$s = (New-Object -ComObject WScript.Shell).CreateShortCut("{shortcut}"); Write-Output $s.TargetPath'
                        r = subprocess.run(
                            ["powershell.exe", "-NoProfile", "-Command", ps],
                            capture_output=True, text=True, timeout=5
                        )
                        target = r.stdout.strip()
                        if target:
                            results.append(f"{base}|||{target}")
                    except:
                        results.append(f"{base}|||{shortcut}")
    
    return results

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: find_program.py <name>")
        sys.exit(1)
    
    name = sys.argv[1]
    results = find_program(name)
    
    if results:
        for r in results:
            print(r)
    else:
        print("NOT_FOUND")
