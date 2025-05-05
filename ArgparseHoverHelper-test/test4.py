from config import parser, parser1

args = parser.parse_args()
print(args.lr)
print(args.e)
print(args.bz)
print(args.val)
print(args.test)
var = args.lr

args1 = parser1.parse_args()
print(args1.lr)
print(args1.e)
print(args1.bz)
print(args1.val)
print(args1.test)
var1 = args1.lr

#----------------------------------#
from config import build_parser, build_parser1

args = build_parser().parse_args()
print(args.lr)

args1 = build_parser1().parse_args()
print(args1.lr)

#----------------------------------#
import config as c

args = c.build_parser().parse_args()
print(args.lr)

args1 = c.build_parser1().parse_args()
print(args1.lr)

#----------------------------------#
from folder.config_folder import parser as p
from folder.config_folder import parser1 as p1

args = p.parse_args()
print(args.lr)
print(args.e)
print(args.bz)
print(args.val)
print(args.test)
var = args.lr

args1 = p1.parse_args()
print(args1.lr)
print(args1.e)
print(args1.bz)
print(args1.val)
print(args1.test)
var1 = args1.lr