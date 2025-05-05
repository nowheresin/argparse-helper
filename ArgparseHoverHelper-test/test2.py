import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--lr', default=1e-5)
parser.add_argument('--epochs', dest='e', default=200)
parser.add_argument('--bz')
parser.add_argument('--val', action='store_true')
args = parser.parse_args()

parser1 = argparse.ArgumentParser()
parser1.add_argument('--lr', default=0.01)
parser1.add_argument('--epochs', dest='e', default=50)
parser1.add_argument('--bz')
parser1.add_argument('--val', action='store_false')
parser1.add_argument('--test', action='store_true')
args1 = parser1.parse_args()

print(args.lr)
print(args.e)
print(args.bz)
print(args.val)
print(args.test)
var = args.lr

print(args1.lr)
print(args1.e)
print(args1.bz)
print(args1.val)
print(args1.test)
var1 = args1.lr



