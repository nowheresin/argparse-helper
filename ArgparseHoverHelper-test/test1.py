import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--lr', default=1e-5)
parser.add_argument('--epochs', dest='e', default=200)
parser.add_argument('--bz')
parser.add_argument('--val', action='store_true')
args = parser.parse_args()

"""
Test Auto-Completion.
"""
var = args  # tap '.', and you can see optional completion items.

"""
Test Quick-Info-on-Hover.
For 'args.lr', Hovering Over 'lr', and the tooltip will show out.
"""
print(args.lr)
print(args.e)
print(args.epochs)  # This is a wrong usage, but our plugin still support it.
print(args.bz)
print(args.val)
print(args.test)

"""
Also, test Jump-to-Definition.
For 'args.lr', Click 'lr' first, then Press 'alt+a', and Jump directly to its 'add_argument' definition..
"""
print(args.lr)
print(args.e)
print(args.epochs)  # This is a wrong usage, but our plugin still support it.
print(args.bz)
print(args.val)
print(args.test)  # args.test is undefined, so cannot Jump.
