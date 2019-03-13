# Data_Stream_peak_finding
Finding peaks in a data stream
1. Create a file called testRR
2. Download the *standing_still_patch_below_left_pec_RR.xlsx* and save it in the testRR; - also download *calculations.java* and *rrcalculator.java*.

- Download BlueJ: go to link http://www.dukelearntoprogram.com/downloads/bluej.php; click *"Download the Duke/Coursera specific version"*.

- Install Jupyter Notebook

- After installed BlueJ

  - create a package called *mainactivity* 
  - inside the package, import two classes: *calculations.java*, *rrcalculator.java*
  - then compile the two classes 

- In BlueJ, right click *calculations.java*, click *new mainactivity.calculations()*, a window called *BlueJ: Create Object* appears and click *OK*. Then right click the red knob, click *void main()*;

- Open command prompt; install 2 libraries: pandas and matplotlib.pyplot by typing:

  - pip3  install pandas
  - pip3 install matplotlib

- After successfully installed, type *jupyter notebook*; once the window opens, navigate to *testRR* file. Click *New* and click *Python 3*. In the *'Untitled'* notebook, type the following code:

  ```Python
  import pandas as pd
  import matplotlib.pyplot as plt
  
  # Load files
  smooth = pd.read_csv("testing3.csv")
  smooth = smooth.values.tolist()
  index = pd.read_csv("testing7.csv")
  index = index.values.tolist()
  
  m = []
  for i in index:
      m.append(i[0])
  m=list(set(m))
  m.sort()
  
  n = []
  for j in smooth:
      n.append(j[0])
  
  new_smooth = []
  for i in m[:len(m)-1]:
      new_smooth.append(n[i])
      
  print(len(m)) 
  plt.plot(smooth)
  plt.plot(m[:len(m)-1],new_smooth,"*")
  ```



 

  
  
