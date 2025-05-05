import argparse

def build_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lr', default=0.01)
    return parser

def build_parser1():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lr', default=1e-5)
    return parser

args = build_parser().parse_args()
print(args.lr)

args1 = build_parser1().parse_args()
print(args1.lr)
