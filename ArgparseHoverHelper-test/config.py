import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--lr', default=1e-5)
parser.add_argument('--epochs', dest='e', default=200)
parser.add_argument('--bz')
parser.add_argument('--val', action='store_true')

parser1 = argparse.ArgumentParser()
parser1.add_argument('--lr', default=0.01)
parser1.add_argument('--epochs', dest='e', default=50)
parser1.add_argument('--bz')
parser1.add_argument('--val', action='store_false')
parser1.add_argument('--test', action='store_true')

def build_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lr', default=0.01)
    return parser

def build_parser1():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lr', default=1e-5)
    return parser
