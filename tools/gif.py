#!/usr/bin/env python3
"""
Makes a GIF from the dev client's frame shots (shots/screenshots/<prefix>_NN.png), cropped and scaled down.

Usage: gif.py <screenshots dir> <prefix> <out.gif> [left top right bottom] [frame ms]
"""
import glob
import os
import sys

from PIL import Image


def main():
    folder, prefix, out = sys.argv[1:4]
    box = tuple(int(v) for v in sys.argv[4:8]) if len(sys.argv) >= 8 else None
    ms = int(sys.argv[8]) if len(sys.argv) >= 9 else 50
    files = sorted(glob.glob(os.path.join(folder, prefix + '_*.png')))
    frames = []
    for f in files:
        im = Image.open(f).convert('RGB')
        if box:
            im = im.crop(box)
        frames.append(im.convert('P', palette=Image.ADAPTIVE, colors=128))
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True)
    print(len(frames), 'frames ->', out)


if __name__ == '__main__':
    main()
