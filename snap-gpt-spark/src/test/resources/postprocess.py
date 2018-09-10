#!/usr/bin/python3

import glob
import sys

if len(sys.argv) is 2:
    filename = sys.argv[1]
    files = glob.glob(filename )
    if(len(files) is not 1):
        raise ValueError( 'Found too many or too few files for postprocessing: ' + str(files))
    #file found, postprocessing can happen here!
    print("Processing file: " + files[0])
else:
    raise ValueError('Invalid argument list: ' + str(sys.argv))
